# 04 — Restrições de plataforma

Este é o documento mais importante do SOT. Ele define o que é **possível**, e
portanto o que pode ser prometido. Toda afirmação aqui tem fonte primária.

## Tabela de ciclo de vida

O que acontece com a Sentinela **depois de armada**, em cada situação:

| Situação | Android | iOS |
|---|---|---|
| Tela apagada | ✅ Sim | ✅ Sim, com ressalvas |
| Aparelho bloqueado | ✅ Sim | ✅ Enquanto a sessão de áudio seguir válida |
| Usuária abriu outro aplicativo | ✅ Sim | ✅ Enquanto o modo de áudio em background seguir ativo |
| Camaleão saiu da tela | ✅ Sim | ✅ Sim |
| Tela do app removida dos Recentes | ⚠️ Pode continuar (Foreground Service, `stopWithTask="false"`) | ❌ Sem garantia |
| Sistema matou o processo | ⚠️ Recuperação possível, **não garantida** | ❌ Sem garantia de relançamento |
| Usuária deu "Forçar parada" | ❌ Não | ❌ Não (force quit equivale) |
| Telefone acabou de reiniciar | ❌ Não se pode iniciar serviço de microfone sozinho | ❌ Não |
| Microfone invisível ao sistema | ❌ Não | ❌ Não |
| Funcionar como "Ok Google" com o app morto | ❌ Não, para app comum | ❌ Não |

**Leitura correta da tabela:** a Sentinela é uma **sessão de proteção ativa**, com
começo marcado pela usuária e fim que pode ser imposto pelo sistema ou por quem
mexer no aparelho. Não é um serviço do sistema operacional.

## Android

### Caminho oficialmente suportado

Foreground Service do tipo `microphone`, iniciado **enquanto a Activity está
visível**:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<!-- só se o SOS realmente usar localização -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<service
    android:name=".sentinela.SentinelaService"
    android:exported="false"
    android:stopWithTask="false"
    android:foregroundServiceType="microphone" />
```

Permissões, com o motivo de cada uma:

| Permissão | Motivo |
|---|---|
| `RECORD_AUDIO` | microfone |
| `FOREGROUND_SERVICE` | serviço persistente permitido |
| `FOREGROUND_SERVICE_MICROPHONE` | obrigatório para FGS de microfone em targets modernos |
| `POST_NOTIFICATIONS` | exibir a notificação do serviço em versões modernas |
| `RECEIVE_BOOT_COMPLETED` | **restaurar configuração** após reboot — não religar o microfone |
| `ACCESS_FINE_LOCATION` | só se o SOS capturar GPS |
| `FOREGROUND_SERVICE_LOCATION` | só se houver FGS de localização |
| `ACCESS_BACKGROUND_LOCATION` | só se o caso realmente exigir, e for aprovado |

A partir do Android 14, o sistema exige o tipo de serviço correto além das
permissões ([tipos de FGS obrigatórios](https://developer.android.com/about/versions/14/changes/fgs-types-required)).
Android 12+ restringe criar foreground service com o app já em background
([restrições de início em background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)) —
daí o requisito RF-20 de armar com o app em primeiro plano.

`RECORD_AUDIO` é permissão **"while-in-use"**: o sistema valida a condição no momento
em que o serviço de microfone é criado. Iniciar um FGS de microfone a partir de
background, em situação não isenta, gera `SecurityException`.

### A notificação é obrigatória

`startForeground()` recebe a notificação que fica exibida enquanto o serviço roda
([Service](https://developer.android.com/reference/android/app/Service),
[visão geral de serviços](https://developer.android.com/develop/background-work/services)).
Não há como suprimi-la.

→ As implicações de **segurança** disso estão em
[C1 do modelo de ameaça](02-modelo-de-ameaca.md). Elas são mais sérias do que o
problema estético.

### Remoção dos Recentes

O Android chama `Service.onTaskRemoved()` quando a usuária remove a task. Com
`android:stopWithTask="false"` (o padrão), o serviço **pode** continuar
([elemento `<service>`](https://developer.android.com/guide/topics/manifest/service-element)).
`START_STICKY` pode ajudar na recuperação após encerramentos do sistema, mas não
é garantia de imortalidade.

### Boot — e o padrão correto de rearme

Apps com target Android 14+ **não podem** lançar um foreground service de microfone a
partir de `BOOT_COMPLETED`
([mudanças no Android 15](https://developer.android.com/about/versions/15/behavior-changes-15)).

A estratégia suportada é: **restaurar o estado e pedir uma interação do usuário.**

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        // Apenas restaura o desejo da usuária de permanecer protegida.
        if (ProtectionPreferences(context).wasArmed()) {
            // NÃO iniciar o microfone aqui.
            // Notificação que leva a uma ação explícita de reativação.
            RearmNotification.show(context)
        }
    }
}
```

O comportamento fica próximo de um despertador no que é tecnicamente permitido:
configuração e estado sobrevivem ao reboot, o microfone precisa ser rearmado.

→ **Não prometer** "religou o telefone e voltou a ouvir". E, por
[C9](02-modelo-de-ameaca.md) e ADR
[0006](../.claude/decisions/0006-maquina-de-estados-do-alerta.md): depois do reboot o
estado exibido é **"desarmada — toque para reativar"**, nunca "protegida". Implica
RF-24, RF-26 e RF-28.

### `SpeechRecognizer`: descartado

A documentação diz que a API pode transmitir áudio a servidores e **não foi
projetada para reconhecimento contínuo**, por consumo de bateria e banda
([SpeechRecognizer](https://developer.android.com/reference/kotlin/android/speech/SpeechRecognizer)).
Contra-requisito CR-04.

## iOS

### O que funciona

Sessão de áudio iniciada com o app aberto, modo de background `audio` declarado:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>O microfone é usado quando você ativa a proteção por voz, para
reconhecer localmente sua frase de segurança.</string>

<key>UIBackgroundModes</key>
<array><string>audio</string></array>
```

A Apple documenta Background Modes como mecanismo para apps continuarem executando
quando normalmente seriam suspensos, e recomenda parcimônia pelo impacto em bateria
([modos de execução em background](https://developer.apple.com/documentation/xcode/configuring-background-execution-modes)).

### O que não funciona

Force quit encerra. A Apple trata o force quit como sinal de que a usuária não quer
mais aquele app rodando em background, e **não o relança** salvo exceções de
subsistema muito específicas — como certos cenários de acessório Bluetooth — que não
se aplicam aqui ([forums 806013](https://developer.apple.com/forums/thread/806013),
[forums 62005](https://developer.apple.com/forums/thread/62005)).

Interrupções de sessão de áudio são reais e precisam ser tratadas, não ignoradas
([forums 813278](https://developer.apple.com/forums/thread/813278)). A lista a cobrir:
**chamada telefônica, Siri, troca de dispositivo Bluetooth, perda de rota de áudio e
suspensão.**

Não há API geral equivalente a `BOOT_COMPLETED` para um app comum iniciar
silenciosamente um monitor de microfone após reboot. Ao reboot ou encerramento real do
processo, a proteção é marcada como **não ativa** até que a sessão de áudio seja
criada de novo.

### O problema prático: build iOS do Vosk

O sherpa-onnx está bem no iOS: exemplos Swift, `Package.swift` declarando iOS 15+,
`XCFramework` distribuído, e caminho de build manual documentado
([build para iOS](https://k2-fsa.github.io/sherpa/onnx/ios/build-sherpa-onnx-swift.html)).

O **Vosk não**. Apesar de declarar suporte a iOS e ter código iOS no repositório, a
página oficial de instalação continua dizendo que o build iOS está "available on
request", ao contrário do artefato Android pronto
([instalação](https://alphacephei.com/vosk/install)). Uma build reproduzível
provavelmente exige compilar a lib nativa, empacotar como `XCFramework` e criar ponte
C/Objective-C++/Swift.

→ Isso merece **prova de conceito isolada e antecipada**, não um item para a última
fase. Ver ADR [0003](../.claude/decisions/0003-android-primeiro.md).

→ Consequência direta: **uma frase falada para um Camaleão terminado não o
ressuscita.** Não existe processo recebendo o áudio naquele instante. É exatamente
nisso que Siri e "Ok Google" diferem: ocupam uma posição no sistema operacional que
um app de terceiros não tem.

### Indicadores de privacidade

Desde o iOS 14 há indicador laranja quando um app usa o microfone, e o Control
Center mostra qual app o usou recentemente
([indicadores da barra de status](https://support.apple.com/fr-be/108331),
[controle de acesso a hardware](https://support.apple.com/en-euro/guide/iphone/iph168c4bbd5/ios)).

A regra **2.5.14** das App Review Guidelines exige consentimento explícito e
indicação visual e/ou sonora clara quando um app registra atividade com microfone,
câmera ou gravação de tela. A **2.5.4** exige que modos de background sejam usados
para suas finalidades previstas
([App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)).

→ RNF-09: não tentamos ocultar nem contornar isso. Nunca.

## Texto que a Área protegida deve exibir

Isto não é sugestão de UX; é o cumprimento de RF-27.

```
PROTEÇÃO POR VOZ
● Armada

A frase é reconhecida enquanto este modo estiver armado.

A proteção por voz é ENCERRADA se:
• você encerrar o aplicativo manualmente;
• o sistema interromper a sessão de áudio;
• a permissão do microfone for removida;
• o telefone for reiniciado.

Último sinal de vida: hoje, 21:43
[ Testar minha frase ]
```

E, depois de um reboot — jamais "Armada":

```
PROTEÇÃO POR VOZ
○ Desarmada

O telefone foi reiniciado. O sistema não permite
que a proteção por voz volte sozinha.

[ Reativar proteção ]
```

## Distribuição e custo de licença

| | |
|---|---|
| Android, desenvolvimento e teste | R$ 0 |
| iOS, desenvolvimento e teste no próprio aparelho | R$ 0, com Personal Team gratuita |
| Personal Team: limitações | até 10 App IDs, 3 dispositivos, 3 apps por dispositivo; o perfil **expira em 7 dias** e exige recompilar ([visão geral da conta](https://developer.apple.com/help/account/basics/about-your-developer-account)) |
| TestFlight / App Store | Apple Developer Program, US$ 99/ano ([programa](https://developer.apple.com/programs)) |
| Instituição de ensino credenciada | pode solicitar **isenção da taxa**, institucionalmente — não é isenção de estudante ([isenção de taxa](https://developer.apple.com/br/help/account/membership/fee-waivers)) |

Para o protótipo: **Android primeiro** (ADR 0003). O perfil de 7 dias da Personal
Team é incompatível com um aparelho que precisa ficar armado por semanas.
