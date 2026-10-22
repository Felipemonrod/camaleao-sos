# Kit de teste de SMS — Camaleão SOS

Testa o envio de SMS real do app num celular Android, conectado ao PC por Wi-Fi.
Não precisa de Android Studio nem de compilar nada.

## Precisa ter

- Windows com PowerShell
- **adb**: Android Studio instalado, ou o "SDK Platform-Tools"
  ([download oficial](https://developer.android.com/tools/releases/platform-tools))
  extraído aqui como `platform-tools\`
- Celular Android 11+ (64 bits), com chip que envia SMS
- PC e celular na mesma rede (de preferência o hotspot do celular)

## Usando com o Claude Code

Abra esta pasta no Claude Code e peça: *"conecte meu celular por Wi-Fi e rode o teste de
SMS"*. O `CLAUDE.md` daqui ensina o agente. Você vai digitar o código de pareamento e
autorizar o número no chat.

## Usando sozinho

```powershell
.\teste-sms.ps1 -Pareamento 192.168.0.10:37123          # digite o código do celular
.\teste-sms.ps1 -Conexao 192.168.0.10:41555 -SoDiagnostico
.\teste-sms.ps1 -Conexao 192.168.0.10:41555 -Numero +5564999999999 -Confirmado
```

Detalhes, códigos de erro e o que o teste faz: [CLAUDE.md](CLAUDE.md).
O app de teste aparece como **Cálculos** e tem seus dados apagados ao final.
