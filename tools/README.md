# tools/

Scripts de apoio. Nenhum entra no APK. Os que rodam no CI estão marcados.

| Arquivo | Para que serve |
|---|---|
| `verificar-sot.py` | Confere as invariantes do Sistema SOT. **CI**; rodar antes de todo commit |
| `preparar-modelos.py` | Baixa o modelo Vosk aprovado, confere o SHA-256 e extrai em `app/src/main/assets/models/`. **CI** |
| `validar.ps1` | Bateria completa: compila, testes unitários, Lint, Painel e, com `-Serial`, os testes instrumentados no aparelho |
| `gerar-amostras.ps1` | Gera as amostras de voz sintética dos testes instrumentados (`app/src/androidTest/assets/`, ignorado) |
| `preparar-notebook.ps1` | Prepara o notebook da apresentação para o Painel; `-Ip <ip> -Renovar` ao trocar de rede |
| `empacotar.ps1` | Monta o pacote de entrega do projeto (código, modelo e evidências) em `artifacts/` |
| `empacotar-kit-sms.ps1` | Monta `artifacts/Camaleao-kit-teste-sms.zip` com o APK de depuração e os testes |
| `kit-sms/` | Kit para testar SMS real num celular pelo adb; ver `kit-sms/LEIA-ME.md` |
| `sms-probe/` | App mínimo que só envia um SMS e registra a resposta do Android; separa defeito do aparelho de defeito do Camaleão |
| `voz/` | Corpus sintético (`gerar_corpus.py`, `textos.py`) e medição da detecção (`avaliar.py`); dados em `dados-brutos/`, ignorado |

Nada aqui guarda segredo. A chave de assinatura e as senhas ficam fora do repositório
(`keystore.properties`, ignorado); sem elas, o build gera só o APK de depuração.
