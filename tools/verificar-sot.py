#!/usr/bin/env python3
"""
Verificador das invariantes do Sistema SOT.

Checa o que e checavel automaticamente (invariante I6 de SISTEMA-SOT.md):

  V1  links internos resolvem
  V2  ADRs tem front matter valido (data + status de uma lista fechada)
  V3  todo ADR aparece na tabela de decisoes do SOT.md
  V4  todo ID de requisito citado (RF/RNF/CR) existe em docs/03
  V5  toda questao aberta citada (Qn) existe em docs/10
  V6  todo risco citado (Rn) existe em docs/10
  V7  todo documento docs/NN-*.md aparece no indice do SOT.md
  V8  referencias/ nao contem marcadores de citacao nao resolvidos
  V9  nenhum ADR 'aceito' foi marcado tambem como substituido sem apontar numero

Uso:
    python tools/verificar-sot.py          # da raiz do repositorio
    python tools/verificar-sot.py --quiet  # so erros

Saida: codigo 0 se tudo passou, 1 se houve falha.
"""

from __future__ import annotations

import argparse
import os
import re
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

STATUS_VALIDOS = ("proposto", "aceito", "rejeitado", "substituido por", "substituído por")

# Pastas que nao sao documentacao do SOT
IGNORAR_DIRS = {".git", "__pycache__", ".venv", "venv", "build", ".gradle", ".idea", "modelos"}


class Resultado:
    def __init__(self) -> None:
        self.falhas: list[tuple[str, str]] = []
        self.avisos: list[tuple[str, str]] = []
        self.ok: list[str] = []

    def falha(self, check: str, msg: str) -> None:
        self.falhas.append((check, msg))

    def aviso(self, check: str, msg: str) -> None:
        self.avisos.append((check, msg))

    def passou(self, check: str) -> None:
        self.ok.append(check)


def arquivos_md() -> list[str]:
    saida = []
    for dp, dn, fn in os.walk(RAIZ):
        dn[:] = [d for d in dn if d not in IGNORAR_DIRS]
        for f in fn:
            if f.endswith(".md"):
                saida.append(os.path.join(dp, f))
    return sorted(saida)


def ler(caminho: str) -> str:
    with open(caminho, encoding="utf-8") as fh:
        return fh.read()


def rel(caminho: str) -> str:
    return os.path.relpath(caminho, RAIZ).replace("\\", "/")


def sem_blocos_de_codigo(texto: str) -> str:
    """Remove blocos cercados, para nao caçar IDs dentro de exemplos."""
    return re.sub(r"```.*?```", "", texto, flags=re.DOTALL)


# ---------------------------------------------------------------- V1

def v1_links(res: Resultado) -> None:
    for caminho in arquivos_md():
        dirpath = os.path.dirname(caminho)
        texto = ler(caminho)
        for m in re.finditer(r"\]\(((?!https?:|mailto:|#)[^)\s]+)", texto):
            alvo = m.group(1).split("#")[0]
            if not alvo:
                continue
            resolvido = os.path.normpath(os.path.join(dirpath, alvo))
            if not os.path.exists(resolvido):
                res.falha("V1", f"{rel(caminho)} -> link quebrado: {alvo}")
    res.passou("V1 links internos")


# ---------------------------------------------------------------- V2, V3, V9

def adrs() -> list[str]:
    pasta = os.path.join(RAIZ, ".claude", "decisions")
    if not os.path.isdir(pasta):
        return []
    return sorted(
        os.path.join(pasta, f)
        for f in os.listdir(pasta)
        if re.match(r"^\d{4}-.+\.md$", f)
    )


def v2_v9_front_matter(res: Resultado) -> None:
    for caminho in adrs():
        texto = ler(caminho)
        m = re.match(r"^---\n(.*?)\n---", texto, flags=re.DOTALL)
        if not m:
            res.falha("V2", f"{rel(caminho)}: sem front matter")
            continue
        fm = m.group(1)
        if not re.search(r"^data:\s*\S+", fm, flags=re.MULTILINE):
            res.falha("V2", f"{rel(caminho)}: front matter sem 'data'")
        st = re.search(r"^status:\s*(.+)$", fm, flags=re.MULTILINE)
        if not st:
            res.falha("V2", f"{rel(caminho)}: front matter sem 'status'")
            continue
        status = st.group(1).strip().lower()
        if not any(status.startswith(v) for v in STATUS_VALIDOS):
            res.falha(
                "V2",
                f"{rel(caminho)}: status '{status}' fora da lista "
                f"(SISTEMA-SOT.md secao 5)",
            )
        if status.startswith(("substituido por", "substituído por")):
            if not re.search(r"\d{4}", status):
                res.falha("V9", f"{rel(caminho)}: 'substituido por' sem numero do ADR")
    res.passou("V2 front matter dos ADRs")
    res.passou("V9 coerencia de status")


def v3_adr_no_indice(res: Resultado) -> None:
    sot = os.path.join(RAIZ, "SOT.md")
    if not os.path.exists(sot):
        res.falha("V3", "SOT.md nao existe")
        return
    texto = ler(sot)
    for caminho in adrs():
        nome = os.path.basename(caminho)
        if nome not in texto:
            res.falha("V3", f"ADR {nome} nao aparece na tabela de decisoes do SOT.md")
    res.passou("V3 ADRs no indice")


# ---------------------------------------------------------------- V4

def v4_requisitos(res: Resultado) -> None:
    req_doc = os.path.join(RAIZ, "docs", "03-requisitos.md")
    if not os.path.exists(req_doc):
        res.falha("V4", "docs/03-requisitos.md nao existe")
        return
    texto_req = ler(req_doc)
    # IDs definidos: aparecem como celula de tabela, no inicio da linha
    definidos = set(re.findall(r"^\|\s*~?~?(R[FN]F?-\d+|CR-\d+)", texto_req, flags=re.MULTILINE))
    definidos |= set(re.findall(r"^\|\s*\*\*(R[FN]F?-\d+|CR-\d+)\*\*", texto_req, flags=re.MULTILINE))

    if not definidos:
        res.falha("V4", "nenhum ID de requisito encontrado em docs/03")
        return

    for caminho in arquivos_md():
        if caminho == req_doc or "_templates" in caminho or "referencias" in caminho:
            continue
        texto = sem_blocos_de_codigo(ler(caminho))
        for rid in set(re.findall(r"\b(RF-\d+|RNF-\d+|CR-\d+)\b", texto)):
            if rid not in definidos:
                res.falha("V4", f"{rel(caminho)}: cita {rid}, que nao existe em docs/03")
    res.passou(f"V4 requisitos ({len(definidos)} IDs definidos)")


# ---------------------------------------------------------------- V5, V6

def v5_v6_questoes_e_riscos(res: Resultado) -> None:
    doc = os.path.join(RAIZ, "docs", "10-riscos-e-questoes-abertas.md")
    if not os.path.exists(doc):
        res.falha("V5", "docs/10-riscos-e-questoes-abertas.md nao existe")
        return
    texto10 = ler(doc)
    qs = set(re.findall(r"^###\s+(Q\d+)", texto10, flags=re.MULTILINE))
    rs = set(re.findall(r"\*\*(R\d+)\s*—", texto10))
    rs |= set(re.findall(r"^###?\s+(R\d+)\s*—", texto10, flags=re.MULTILINE))
    rs |= set(re.findall(r"^##\s+(R\d+)\s", texto10, flags=re.MULTILINE))

    for caminho in arquivos_md():
        if caminho == doc or "_templates" in caminho or "referencias" in caminho:
            continue
        texto = sem_blocos_de_codigo(ler(caminho))
        for qid in set(re.findall(r"\b(Q\d+)\b", texto)):
            if qid not in qs:
                res.falha("V5", f"{rel(caminho)}: cita {qid}, que nao existe em docs/10")
        for rid in set(re.findall(r"\[(R\d+)\]", texto)):
            if rid not in rs:
                res.falha("V6", f"{rel(caminho)}: cita risco {rid}, que nao existe em docs/10")
    res.passou(f"V5 questoes abertas ({len(qs)} definidas)")
    res.passou(f"V6 riscos ({len(rs)} definidos)")


# ---------------------------------------------------------------- V7

def v7_docs_no_indice(res: Resultado) -> None:
    sot = ler(os.path.join(RAIZ, "SOT.md"))
    pasta = os.path.join(RAIZ, "docs")
    for f in sorted(os.listdir(pasta)):
        if not re.match(r"^\d{2}-.+\.md$", f):
            continue
        if f not in sot:
            res.falha("V7", f"docs/{f} nao aparece no indice do SOT.md")
    res.passou("V7 documentos no indice")


# ---------------------------------------------------------------- V8

def v8_citacoes_resolvidas(res: Resultado) -> None:
    pasta = os.path.join(RAIZ, "referencias")
    if not os.path.isdir(pasta):
        return
    for f in sorted(os.listdir(pasta)):
        if not f.endswith(".md"):
            continue
        texto = ler(os.path.join(pasta, f))
        sobras = re.findall(r"cite(?:turn|\w)*turn\d+\w+", texto)
        if sobras:
            res.aviso(
                "V8",
                f"referencias/{f}: {len(sobras)} marcadores de citacao nao resolvidos",
            )
    res.passou("V8 citacoes nas referencias")


# ---------------------------------------------------------------- main

def main() -> int:
    ap = argparse.ArgumentParser(description="Verifica as invariantes do Sistema SOT.")
    ap.add_argument("--quiet", action="store_true", help="imprime so falhas")
    args = ap.parse_args()

    res = Resultado()
    v1_links(res)
    v2_v9_front_matter(res)
    v3_adr_no_indice(res)
    v4_requisitos(res)
    v5_v6_questoes_e_riscos(res)
    v7_docs_no_indice(res)
    v8_citacoes_resolvidas(res)

    if not args.quiet:
        for check in res.ok:
            print(f"  ok   {check}")
        for check, msg in res.avisos:
            print(f"  aviso {check}: {msg}")

    if res.falhas:
        print()
        for check, msg in res.falhas:
            print(f"  FALHA {check}: {msg}")
        print(f"\n{len(res.falhas)} falha(s). Veja SISTEMA-SOT.md.")
        return 1

    print(f"\nSOT consistente. {len(res.ok)} verificacoes, {len(res.avisos)} aviso(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
