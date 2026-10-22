"""Mede a detecção da Frase de Segurança e dos comandos no corpus sintético.

Reproduz no PC o que o app faz no aparelho: mesmo modelo Vosk, PCM 16 kHz em blocos
de 1600 amostras, resultado final por endpoint, gramática restrita e PhraseMatcher.
As estratégias são comparadas lado a lado; a escolhida é portada para o Kotlin.

Uso: .venv/Scripts/python tools/voz/avaliar.py [estrategia ...] [--rapido]
"""
import json
import sys
import zlib
import unicodedata
import re
from collections import defaultdict
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import fftconvolve, lfilter

sys.path.insert(0, str(Path(__file__).parent))
from textos import COMANDOS, FRASES

ROOT = Path(__file__).resolve().parents[2]
CORPUS = ROOT / "dados-brutos" / "voz"
MODEL = ROOT / "app" / "src" / "main" / "assets" / "models" / "vosk-pt"
SR = 16000

# ---------------------------------------------------------------- PhraseMatcher (igual ao Kotlin)


def normalize(value: str) -> str:
    v = unicodedata.normalize("NFD", value.lower())
    v = re.sub(r"\p{M}+" if False else r"[̀-ͯ]+", "", v)
    v = re.sub(r"[^a-z0-9 ]", " ", v).strip()
    return re.sub(r"\s+", " ", v)


def gram(value: str) -> str:
    """Forma para a gramática do Vosk: minúsculas, sem pontuação, COM acentos.
    O vocabulário do modelo é acentuado ("localização", "não"); sem acento a palavra
    é descartada da gramática com um simples aviso no log."""
    v = re.sub(r"[^\w ]", " ", value.lower())
    return re.sub(r"\s+", " ", v).strip()


def words(value: str):
    return [w for w in normalize(value).split(" ") if w]


def similarity(a: str, b: str) -> float:
    x, y = normalize(a), normalize(b)
    if not x or not y:
        return 0.0
    row = list(range(len(y) + 1))
    for i, c in enumerate(x):
        nxt = [i + 1] + [0] * len(y)
        for j, d in enumerate(y):
            nxt[j + 1] = min(nxt[j] + 1, row[j + 1] + 1, row[j] + (0 if c == d else 1))
        row = nxt
    return 1.0 - row[-1] / max(len(x), len(y))


def accepts_baseline(expected, actual, threshold):
    wanted, heard = words(expected), words(actual)
    if len(wanted) < 3 or len(heard) != len(wanted) or "unk" in heard:
        return False
    if similarity(wanted[0], heard[0]) < 0.9:
        return False
    return similarity(expected, actual) >= threshold


def calibrated_threshold(positives, negatives, expected):
    if len(positives) < 3 or not negatives:
        return None
    floor = min(similarity(expected, p) for p in positives)
    ceiling = max(similarity(expected, n) for n in negatives)
    if floor <= ceiling or floor < 0.9:
        return None
    return (floor + ceiling) / 2


# ---------------------------------------------------------------- condições acústicas


def pink(n, rng):
    white = rng.standard_normal(n)
    b = [0.049922035, -0.095993537, 0.050612699, -0.004408786]
    a = [1, -2.494956002, 2.017265875, -0.522189400]
    return lfilter(b, a, white)


def mix(signal, noise, snr_db):
    ps = np.mean(signal**2) + 1e-12
    pn = np.mean(noise**2) + 1e-12
    return signal + noise * np.sqrt(ps / (pn * 10 ** (snr_db / 10)))


def reverb(signal, rng, rt60=0.5):
    n = int(SR * rt60)
    t = np.arange(n) / SR
    rir = rng.standard_normal(n) * np.exp(-6.9 * t / rt60)
    rir[0] = 1.0
    out = fftconvolve(signal, rir)[: len(signal) + n // 4]
    return out / (np.max(np.abs(out)) + 1e-9) * np.max(np.abs(signal))


CONDICOES = ["limpo", "ruido20", "ruido10", "tv10", "longe"]


def condicionar(audio, cond, seed, babble):
    rng = np.random.default_rng(seed)
    pad_a, pad_b = int(0.6 * SR), int(1.3 * SR)
    x = np.concatenate([np.zeros(pad_a), audio, np.zeros(pad_b)])
    floor = rng.standard_normal(len(x)) * 1e-4
    if cond == "limpo":
        y = x + floor
    elif cond == "ruido20":
        y = mix(x, pink(len(x), rng), 20)
    elif cond == "ruido10":
        y = mix(x, pink(len(x), rng), 10)
    elif cond == "tv10":
        b = np.resize(babble[rng.integers(len(babble))], len(x))
        y = mix(x, b + 0.3 * pink(len(x), rng), 10)
    elif cond == "longe":
        y = mix(reverb(x, rng) * 0.2, pink(len(x) + int(SR * 0.125), rng)[: len(reverb(x, rng))], 15)
    else:
        raise ValueError(cond)
    y = np.clip(y, -1, 1)
    return (y * 32767).astype(np.int16)


# ---------------------------------------------------------------- Vosk


_model = None


def model():
    global _model
    if _model is None:
        from vosk import Model, SetLogLevel

        SetLogLevel(-1)
        _model = Model(str(MODEL))
    return _model


def recognizer(grammar=None):
    from vosk import KaldiRecognizer

    r = KaldiRecognizer(model(), SR, json.dumps(grammar, ensure_ascii=False)) if grammar else KaldiRecognizer(model(), SR)
    r.SetWords(True)
    return r


def decode(rec, pcm):
    """Alimenta como o app: blocos de 1600 amostras; devolve os resultados finais."""
    out = []
    for i in range(0, len(pcm), 1600):
        if rec.AcceptWaveform(pcm[i : i + 1600].tobytes()):
            out.append(json.loads(rec.Result()))
    last = json.loads(rec.FinalResult())
    if last.get("text"):
        out.append(last)
    rec.Reset()
    return [r for r in out if r.get("text")]


# ---------------------------------------------------------------- estratégias de estágio 1


class Baseline:
    """Exatamente o app atual (VoskPipeline.keywordGrammar + PhraseMatcher.accepts)."""

    def __init__(self, phrase):
        self.phrase = phrase
        ws = words(phrase)
        tail = " ".join(ws[1:])
        self.grammar = [normalize(phrase), f"vou {tail}", f"voce {tail}", "[unk]"]  # igual ao app atual
        self.rec = recognizer(self.grammar)
        self.threshold = 1.0

    def texts(self, pcm):
        return [r["text"] for r in decode(self.rec, pcm)]

    def calibrate(self, pos_pcms, neg_pcms):
        pos = [" ".join(self.texts(p)) for p in pos_pcms]
        neg = [" ".join(self.texts(n)) for n in neg_pcms]
        t = calibrated_threshold(pos, neg, self.phrase)
        if t is not None:
            self.threshold = t
        return t is not None

    def detect(self, pcm):
        return any(accepts_baseline(self.phrase, t, self.threshold) for t in self.texts(pcm))


# Palavras frequentes que dão ao decodificador uma saída "barata" para fala comum,
# em vez de forçá-la para a frase ou para [unk]. Ver docs/06.
ISCAS = (
    "a o e de do da que em um uma eu você ele ela não sim vou vai tem tá "
    "para pra com por mais muito agora hoje aqui la isso esse essa meu minha "
    "seu sua casa porta mesa comida conta carro escola casaco cedo tarde "
    "quero preciso vamos olha gente ah bom dia noite onde quando"
).split()


def melhor_janela(alvo, ouvidas):
    """Maior similaridade entre a frase e qualquer janela contígua do mesmo tamanho."""
    n = len(alvo)
    if len(ouvidas) < n:
        return similarity(" ".join(alvo), " ".join(ouvidas)) if ouvidas else 0.0
    return max(similarity(" ".join(alvo), " ".join(ouvidas[i : i + n])) for i in range(len(ouvidas) - n + 1))


class Iscas:
    """Gramática com a frase, as palavras dela e iscas; aceita a frase contígua."""

    def __init__(self, phrase, conf=0.0, iscas=True, palavras=True, junta=False, sem=()):
        self.phrase = phrase
        self.junta = junta
        self.alvo = words(phrase)
        g = [gram(phrase)]
        if palavras:
            g += sorted(set(gram(phrase).split()))
        if iscas:
            g += [w for w in ISCAS if w not in g and w not in sem]
        g.append("[unk]")
        self.rec = recognizer(g)
        self.conf = conf

    def janela(self, pcm):
        """Devolve True se a frase aparece contígua com confiança mínima.
        Com junta=True, procura também através de resultados consecutivos: o
        endpoint do Vosk pode cortar a frase numa micro-pausa."""
        rs = decode(self.rec, pcm)
        if self.junta:
            rs = [{"result": [w for r in rs for w in r.get("result", [])]}]
        for r in rs:
            ws = r.get("result", [])
            ts = [normalize(w["word"]) for w in ws]
            n = len(self.alvo)
            for i in range(len(ts) - n + 1):
                if ts[i : i + n] == self.alvo and min(w["conf"] for w in ws[i : i + n]) >= self.conf:
                    return True
        return False

    def calibrate(self, pos, neg):
        return all(self.janela(p) for p in pos) and not any(self.janela(n) for n in neg)

    def detect(self, pcm):
        return self.janela(pcm)


class Verificada(Iscas):
    """Iscas + segunda leitura do buffer em RAM com vocabulário aberto."""

    def __init__(self, phrase, conf=0.0, verif=0.75, **kw):
        super().__init__(phrase, conf, **kw)
        self.livre = recognizer(None)
        self.verif = verif

    def nota(self, pcm):
        texto = " ".join(r["text"] for r in decode(self.livre, pcm))
        return melhor_janela(self.alvo, words(texto))

    def detect(self, pcm):
        return self.janela(pcm) and self.nota(pcm) >= self.verif

    def calibrate(self, pos, neg):
        return all(self.detect(p) for p in pos) and not any(self.detect(n) for n in neg)


class Varredura(Verificada):
    """Devolve a nota da 2ª leitura (ou -1 sem acerto de gramática) para varrer limiares."""

    LIMIARES = [0.55, 0.60, 0.65, 0.70, 0.75, 0.80]

    def detect(self, pcm):
        return self.nota(pcm) if self.janela(pcm) else -1.0

    def calibrate(self, pos, neg):
        p = [self.detect(x) for x in pos]
        n = [self.detect(x) for x in neg]
        return {t: min(p) >= t and max(n, default=-1) < t for t in self.LIMIARES}


class SoLivre:
    """Referência: só vocabulário aberto + similaridade por janela."""

    def __init__(self, phrase, verif=0.8):
        self.alvo = words(phrase)
        self.livre = recognizer(None)
        self.verif = verif

    def detect(self, pcm):
        texto = " ".join(r["text"] for r in decode(self.livre, pcm))
        return melhor_janela(self.alvo, words(texto)) >= self.verif

    def calibrate(self, pos, neg):
        return all(self.detect(p) for p in pos) and not any(self.detect(n) for n in neg)


ESTRATEGIAS = {
    "base": Baseline,
    "contigua": lambda p: Iscas(p, iscas=False, palavras=False),
    "palavras": lambda p: Iscas(p, iscas=False),
    "iscas": Iscas,
    "iscas-c60": lambda p: Iscas(p, conf=0.6),
    "varredura": Varredura,
    "junta70": lambda p: Verificada(p, verif=0.70, junta=True),
    "junta70-semcurtas": lambda p: Verificada(p, verif=0.70, junta=True, sem=("a", "o", "e", "ah", "olha")),
    "junta65": lambda p: Verificada(p, verif=0.65, junta=True),
    "verif70": lambda p: Verificada(p, verif=0.70),
    "verif80": lambda p: Verificada(p, verif=0.80),
    "livre80": SoLivre,
}

# ---------------------------------------------------------------- execução


def carregar():
    manifest = [json.loads(l) for l in (CORPUS / "manifest.jsonl").read_text("utf-8").splitlines()]
    return manifest


def audio(item):
    data, _ = sf.read(CORPUS / item["arquivo"], dtype="float32")
    return data


def trabalho(args):
    nome, chave, locutor, itens, babble, rapido = args
    phrase = FRASES[chave]
    det = ESTRATEGIAS[nome](phrase)
    meus = [i for i in itens if i["locutor"] == locutor]
    calib_pos = [i for i in meus if i["tipo"] == "cal" and i["frase"] == chave]
    calib_neg = [i for i in meus if i["tipo"] == "dificil" and i["frase"] == chave][:1]
    ok = det.calibrate(
        [condicionar(audio(i), "limpo", 1, babble) for i in calib_pos],
        [condicionar(audio(i), "limpo", 1, babble) for i in calib_neg],
    )
    # Amostras usadas na calibração ficam fora da medição.
    usados = {i["arquivo"] for i in calib_pos + calib_neg}
    alvo = [
        i
        for i in meus
        if i["arquivo"] not in usados
        and (i["tipo"] in ("pos", "pos_ctx", "dificil") and i["frase"] == chave or i["tipo"] == "conversa"
             or i["tipo"] == "dificil")
    ]
    conds = ["limpo", "tv10"] if rapido else CONDICOES
    linhas = []
    for item in alvo:
        a = audio(item)
        for c in conds:
            hit = det.detect(condicionar(a, c, zlib.crc32(item["arquivo"].encode()), babble))
            tipo = item["tipo"]
            if tipo == "dificil" and item["frase"] != chave:
                tipo = "dificil_outra"
            linhas.append((locutor, item["divisao"], chave, tipo, c, hit, item["texto"], len(a) / SR))
    return ok, linhas


def resumo(nome, resultados):
    if isinstance(resultados[0][0], dict):
        for t in Varredura.LIMIARES:
            sub = [
                ({True: True, False: False}[ok[t]], [l[:5] + (l[5] >= t,) + l[6:] for l in ls])
                for ok, ls in resultados
            ]
            resumo(f"{nome} limiar {t:.2f}", sub)
        return
    calib = [ok for ok, _ in resultados]
    linhas = [l for _, ls in resultados for l in ls]
    print(f"\n=== {nome} — calibração aceita em {sum(calib)}/{len(calib)} (locutor×frase)")
    agg = defaultdict(lambda: [0, 0, 0.0])
    for loc, div, chave, tipo, cond, hit, texto, dur in linhas:
        for k in [(div, tipo, cond), (div, tipo, "*")]:
            agg[k][0] += hit
            agg[k][1] += 1
            agg[k][2] += dur
    for div in ["ajuste", "teste"]:
        print(f"  [{div}]  " + "  ".join(f"{c:>8}" for c in CONDICOES + ["*"]))
        for tipo in ["pos", "pos_ctx", "dificil", "dificil_outra", "conversa"]:
            cells = []
            for c in CONDICOES + ["*"]:
                h, n, d = agg[(div, tipo, c)]
                cells.append(f"{100 * h / n:7.1f}%" if n else "       -")
            print(f"  {tipo:>13} " + "  ".join(cells))
        h, n, d = agg[(div, "conversa", "*")]
        if d:
            print(f"  falsos/h em conversa sintética: {h / (d / 3600):.2f} ({h} em {d / 60:.1f} min)")
    # O app não deixa armar se as 3 leituras de calibração falham: a usuária troca de
    # frase. O acerto percebido é o das combinações locutor×frase que calibraram.
    calibrados = [ls for ok, ls in resultados if ok]
    for div in ["ajuste", "teste"]:
        cells = []
        for tipo in ["pos", "pos_ctx", "dificil"]:
            h = [l[5] for ls in calibrados for l in ls if l[1] == div and l[3] == tipo]
            cells.append(f"{tipo} {100 * sum(h) / len(h):.1f}%" if h else f"{tipo} -")
        print(f"  [{div}, só calibrados] " + " · ".join(cells))
    erros = [l for l in linhas if l[1] == "teste" and l[3] in ("dificil", "conversa", "dificil_outra") and l[5]]
    if erros:
        print("  exemplos de falso positivo (teste):", sorted({(l[6], l[4]) for l in erros})[:8])


# ---------------------------------------------------------------- estágio 2: comandos


class Comandos:
    """Gramática de comandos; com iscas, exige o comando contíguo e confiança mínima."""

    def __init__(self, iscas=False, conf=0.0):
        self.cmds = {normalize(t): k for k, t in COMANDOS.items()}
        g = [gram(t) for t in COMANDOS.values()]
        if iscas:
            extra = {w for c in g for w in c.split()} | set(ISCAS)
            g += sorted(w for w in extra if w not in g)
        self.rec = recognizer(g + ["[unk]"])
        self.iscas, self.conf = iscas, conf

    def label(self, pcm):
        for r in decode(self.rec, pcm):
            if not self.iscas:
                if normalize(r["text"]) in self.cmds:
                    return self.cmds[normalize(r["text"])]
                continue
            ws = r.get("result", [])
            ts = [normalize(w["word"]) for w in ws]
            for c, k in self.cmds.items():
                cw = c.split()
                for i in range(len(ts) - len(cw) + 1):
                    if ts[i : i + len(cw)] == cw and min(w["conf"] for w in ws[i : i + len(cw)]) >= self.conf:
                        return k
        return None


COMANDO_ESTRATEGIAS = {
    "cmd-base": Comandos,
    "cmd-iscas": lambda: Comandos(iscas=True),
    "cmd-iscas-c60": lambda: Comandos(iscas=True, conf=0.6),
}


def trabalho_cmd(args):
    nome, locutor, itens, babble, rapido = args
    det = COMANDO_ESTRATEGIAS[nome]()
    linhas = []
    conds = ["limpo", "tv10"] if rapido else CONDICOES
    for item in itens:
        if item["locutor"] != locutor or not (
            item["tipo"].startswith("cmd:") or item["tipo"] in ("nao_cmd", "conversa")
        ):
            continue
        a = audio(item)
        for c in conds:
            got = det.label(condicionar(a, c, zlib.crc32(item["arquivo"].encode()), babble))
            want = item["tipo"][4:] if item["tipo"].startswith("cmd:") else None
            linhas.append((item["divisao"], item["tipo"], c, want, got))
    return linhas


def resumo_cmd(nome, resultados):
    linhas = [l for ls in resultados for l in ls]
    print(f"\n=== {nome}")
    for div in ["ajuste", "teste"]:
        cmd = [l for l in linhas if l[0] == div and l[3]]
        neg = [l for l in linhas if l[0] == div and not l[3]]
        if not cmd:
            continue
        certo = sum(l[3] == l[4] for l in cmd)
        trocado = sum(l[4] is not None and l[3] != l[4] for l in cmd)
        falso = sum(l[4] is not None for l in neg)
        # "cancelar" só fecha a janela; o risco real é uma FUNÇÃO disparada por fala comum.
        funcao = sum(l[4] not in (None, "cancelar") for l in neg)
        print(
            f"  [{div}] comando certo {100 * certo / len(cmd):.1f}% · função trocada "
            f"{100 * trocado / len(cmd):.1f}% · fala comum → função {100 * funcao / len(neg):.2f}% "
            f"({funcao}/{len(neg)}) · → cancelar {falso - funcao}"
        )
        for c in CONDICOES:
            cc = [l for l in cmd if l[2] == c]
            if cc:
                print(f"     {c:>8}: certo {100 * sum(l[3] == l[4] for l in cc) / len(cc):.1f}%")
        exemplos = sorted({(l[1], l[4]) for l in neg if l[4]})[:6]
        if exemplos:
            print("     falsos:", exemplos)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    rapido = "--rapido" in sys.argv
    itens = carregar()
    babble_src = [i for i in itens if i["tipo"] == "conversa" and i["locutor"] in ("maria-0", "jeff-0")]
    babble = [audio(i) for i in babble_src[:40]]
    locutores = sorted({i["locutor"] for i in itens})
    if "--comandos" in sys.argv:
        for nome in args or list(COMANDO_ESTRATEGIAS):
            with ProcessPoolExecutor() as ex:
                res = list(ex.map(trabalho_cmd, [(nome, l, itens, babble, rapido) for l in locutores]))
            resumo_cmd(nome, res)
        return
    nomes = args or list(ESTRATEGIAS)
    for nome in nomes:
        jobs = [(nome, k, loc, itens, babble, rapido) for k in FRASES for loc in locutores]
        with ProcessPoolExecutor() as ex:
            resultados = list(ex.map(trabalho, jobs))
        resumo(nome, resultados)


if __name__ == "__main__":
    main()
