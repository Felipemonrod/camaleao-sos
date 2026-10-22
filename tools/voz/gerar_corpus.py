"""Gera o corpus sintético de voz em dados-brutos/voz (ignorado pelo Git).

Vozes: Microsoft Maria e Daniel (OneCore, via WinRT) e quatro vozes pt-BR do Piper.
Cada combinação voz + tom é tratada como um locutor. O corpus é separado por locutor:
os de "ajuste" servem para escolher parâmetros; os de "teste" nunca entram no ajuste.

Uso: .venv/Scripts/python tools/voz/gerar_corpus.py
"""
import asyncio
import io
import json
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

# O onnxruntime do Piper falha com access violation se carregado depois do WinRT.
import piper  # noqa: F401
from scipy.signal import resample_poly

sys.path.insert(0, str(Path(__file__).parent))
from textos import COMANDOS, CONTEXTOS, CONVERSA, DIFICEIS, FRASES, NAO_COMANDOS

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "dados-brutos" / "voz"
PIPER = ROOT / ".tools" / "piper"
SR = 16000
RATES = [0.9, 1.0, 1.15]

# (nome, motor, voz, tom, divisão). Tom: percentual SSML no WinRT; fator no Piper.
LOCUTORES = [
    ("maria-0", "winrt", "Maria", "+0%", "ajuste"),
    ("maria-alto", "winrt", "Maria", "+15%", "ajuste"),
    ("faber-0", "piper", "pt_BR-faber-medium", 1.0, "ajuste"),
    ("faber-alto", "piper", "pt_BR-faber-medium", 1.25, "ajuste"),
    ("cadu-0", "piper", "pt_BR-cadu-medium", 1.0, "ajuste"),
    ("cadu-alto", "piper", "pt_BR-cadu-medium", 1.25, "ajuste"),
    ("maria-baixo", "winrt", "Maria", "-12%", "teste"),
    ("daniel-0", "winrt", "Daniel", "+0%", "teste"),
    ("daniel-alto", "winrt", "Daniel", "+25%", "teste"),
    ("jeff-0", "piper", "pt_BR-jeff-medium", 1.0, "teste"),
    ("jeff-alto", "piper", "pt_BR-jeff-medium", 1.25, "teste"),
    ("edresson-0", "piper", "pt_BR-edresson-low", 1.0, "teste"),
    ("edresson-alto", "piper", "pt_BR-edresson-low", 1.3, "teste"),
]


def to16k(audio: np.ndarray, rate: int) -> np.ndarray:
    if rate == SR:
        return audio.astype(np.float32)
    g = np.gcd(rate, SR)
    return resample_poly(audio, SR // g, rate // g).astype(np.float32)


class WinRt:
    def __init__(self):
        from winrt.windows.media.speechsynthesis import SpeechSynthesizer

        self.synth = SpeechSynthesizer()
        self.voices = {v.display_name.split()[-1]: v for v in SpeechSynthesizer.all_voices}

    async def _speak(self, voice, pitch, rate, text):
        from winrt.windows.storage.streams import DataReader

        self.synth.voice = self.voices[voice]
        ssml = (
            "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='pt-BR'>"
            f"<prosody pitch='{pitch}' rate='{rate}'>{text}</prosody></speak>"
        )
        stream = await self.synth.synthesize_ssml_to_stream_async(ssml)
        reader = DataReader(stream.get_input_stream_at(0))
        await reader.load_async(stream.size)
        data = bytearray(stream.size)
        reader.read_bytes(data)
        return bytes(data)

    def speak(self, voice, pitch, rate, text):
        audio, rate_hz = sf.read(io.BytesIO(asyncio.run(self._speak(voice, pitch, rate, text))))
        return to16k(audio, rate_hz)


class Piper:
    def __init__(self):
        from piper import PiperVoice

        self.cache = {}
        self.load = lambda name: self.cache.setdefault(
            name, PiperVoice.load(str(PIPER / f"{name}.onnx"))
        )

    def speak(self, voice, factor, rate, text):
        from piper import SynthesisConfig

        v = self.load(voice)
        # Tom: sintetiza `factor` vezes mais lento e acelera por reamostragem.
        # O resultado tem a duração original e o tom multiplicado por `factor`.
        cfg = SynthesisConfig(length_scale=factor / rate, noise_scale=0.667, noise_w_scale=0.8)
        chunks = list(v.synthesize(text, syn_config=cfg))
        audio = np.concatenate([c.audio_float_array for c in chunks])
        rate_hz = chunks[0].sample_rate
        return to16k(audio, int(round(rate_hz / factor)))


def itens():
    for chave, frase in FRASES.items():
        for i, ctx in enumerate(CONTEXTOS):
            # A frase sozinha nas três velocidades simula as 3 leituras de calibração.
            kind = "cal" if i == 0 else "pos_ctx"
            yield kind, chave, ctx.format(f=frase), RATES
        for texto in DIFICEIS[chave]:
            yield "dificil", chave, texto, RATES
    for texto in CONVERSA:
        yield "conversa", "", texto, [1.0]
    for rotulo, texto in COMANDOS.items():
        yield "cmd:" + rotulo, "", texto, RATES
    for texto in NAO_COMANDOS:
        yield "nao_cmd", "", texto, [1.0]
    # Acrescentado no fim para não renumerar arquivos já gerados: leituras da frase
    # sozinha que nunca entram na calibração.
    for chave, frase in FRASES.items():
        yield "pos", chave, frase, [0.8, 0.95, 1.05, 1.25]


def main():
    # Com nomes de locutor na linha de comando, sintetiza só esses (para rodar em
    # paralelo) e não grava o manifest. Sem argumentos, completa o que falta e grava.
    # --manifesto: não sintetiza; lista só locutores já completos.
    parcial = "--manifesto" in sys.argv
    somente = set(a for a in sys.argv[1:] if not a.startswith("--"))
    motores = {} if parcial else {"winrt": WinRt(), "piper": Piper()}
    manifest = []
    for nome, motor, voz, tom, divisao in LOCUTORES:
        if somente and nome not in somente:
            continue
        pasta = OUT / nome
        pasta.mkdir(parents=True, exist_ok=True)
        total = sum(len(r) for *_, r in itens())
        if parcial and len(list(pasta.glob("*.wav"))) < total:
            continue
        n = 0
        for kind, frase, texto, rates in itens():
            for rate in rates:
                arquivo = pasta / f"{n:04d}.wav"
                if not arquivo.exists() and not parcial:
                    audio = motores[motor].speak(voz, tom, rate, texto)
                    peak = float(np.max(np.abs(audio))) or 1.0
                    sf.write(arquivo, (audio / peak * 0.7 * 32767).astype(np.int16), SR)
                manifest.append(
                    {
                        "arquivo": str(arquivo.relative_to(OUT)).replace("\\", "/"),
                        "locutor": nome,
                        "divisao": divisao,
                        "tipo": kind,
                        "frase": frase,
                        "texto": texto,
                        "velocidade": rate,
                    }
                )
                n += 1
        print(f"{nome}: {n} amostras", flush=True)
    if somente and not parcial:
        return
    (OUT / "manifest.jsonl").write_text(
        "\n".join(json.dumps(m, ensure_ascii=False) for m in manifest), encoding="utf-8"
    )
    print(f"Total: {len(manifest)} amostras em {OUT}")


if __name__ == "__main__":
    main()
