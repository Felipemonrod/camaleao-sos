<#
Teste de SMS real do Camaleão SOS em celular conectado por depuração sem fio (ou USB).

Não compila nada: usa os APKs da pasta do kit. Envia SMS real SOMENTE com -Confirmado,
que só deve ser passado depois que o dono do número autorizou o envio.

Exemplos:
  .\teste-sms.ps1 -Pareamento 192.168.0.10:37123            # pareia (pede o código de 6 dígitos)
  .\teste-sms.ps1 -Conexao 192.168.0.10:41555 -SoDiagnostico  # só confere chip/operadora
  .\teste-sms.ps1 -Conexao 192.168.0.10:41555 -Numero +5564999999999 -Confirmado
  .\teste-sms.ps1 -Serial R5XXXX -Numero +5564999999999 -SemLocalizacao -Confirmado
#>
param(
    [string]$Pareamento = '',
    [string]$Conexao = '',
    [string]$Serial = '',
    [string]$Numero = '',
    [switch]$SemLocalizacao,
    [switch]$SoDiagnostico,
    [switch]$Confirmado,
    [switch]$Manter
)
$ErrorActionPreference = 'Stop'
$kit = $PSScriptRoot
$pkg = 'br.com.camaleao.sos'
$runner = 'br.com.camaleao.sos.test/androidx.test.runner.AndroidJUnitRunner'
$teste = 'br.com.camaleao.sos.CompleteFlowTest#alertSendsSmsWithMapLinkAfterPreparation'

function Mascarar([string]$texto) {
    # Telefones e coordenadas nunca vão em claro para o relatório.
    $texto = [regex]::Replace($texto, '\+?\d{8,15}', { param($m) $v = $m.Value; $v.Substring(0, [Math]::Min(3, $v.Length)) + '*****' + $v.Substring($v.Length - 4) })
    [regex]::Replace($texto, '(lat|lon)=[-0-9.]+', '$1=***')
}

# 1. adb: PATH, SDK do Android Studio ou platform-tools dentro do kit.
$adb = @(
    (Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1),
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    (Join-Path $kit 'platform-tools\adb.exe')
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $adb) {
    throw 'adb não encontrado. Baixe o "SDK Platform-Tools" em https://developer.android.com/tools/releases/platform-tools e extraia a pasta platform-tools dentro do kit.'
}
Write-Output "adb: $adb"

# 2. Pareamento sem fio (Android 11+). O código de 6 dígitos é digitado pela pessoa.
if ($Pareamento) {
    Write-Output "Pareando com $Pareamento. Digite o código de 6 dígitos mostrado no celular."
    & $adb pair $Pareamento
    if ($LASTEXITCODE -ne 0) { throw 'Pareamento falhou. Gere um novo código em Depuração sem fio > Parear dispositivo com código.' }
    if (-not $Conexao -and -not $Serial) { Write-Output 'Pareado. Rode de novo com -Conexao IP:PORTA (a porta da tela principal de Depuração sem fio, não a do pareamento).'; return }
}

# 3. Conexão.
if ($Conexao) {
    $out = & $adb connect $Conexao 2>&1
    Write-Output $out
    if (($out -join ' ') -notmatch 'connected') { throw 'Não conectou. Confira se PC e celular estão no mesmo Wi-Fi e se a porta é a da tela principal de Depuração sem fio.' }
    $Serial = $Conexao
}
if (-not $Serial) {
    $lista = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' -and $_ -notmatch '^emulator-' }
    if (@($lista).Count -ne 1) { throw 'Informe -Conexao IP:PORTA ou -Serial. Aparelhos vistos: ' + (& $adb devices | Out-String) }
    $Serial = ($lista -split "`t")[0]
}
if ((& $adb -s $Serial get-state 2>&1) -ne 'device') { throw "Aparelho $Serial não está pronto. Aceite o aviso de depuração no celular." }
function Sh([string]$cmd) { (& $adb -s $Serial shell $cmd 2>&1 | Out-String).Trim() }

# 4. Diagnóstico do aparelho e do chip.
$relatorio = New-Object System.Collections.Generic.List[string]
$info = [ordered]@{
    'Aparelho' = "$(Sh 'getprop ro.product.manufacturer') $(Sh 'getprop ro.product.model')"
    'Android' = "$(Sh 'getprop ro.build.version.release') (API $(Sh 'getprop ro.build.version.sdk'))"
    'ABI' = Sh 'getprop ro.product.cpu.abi'
    'Estado dos chips' = Sh 'getprop gsm.sim.state'
    'Operadora' = Sh 'getprop gsm.sim.operator.alpha'
    'Rede' = Sh 'getprop gsm.network.type'
    'Modo avião' = Sh 'settings get global airplane_mode_on'
    'Chip padrão de SMS' = ((Sh 'dumpsys isub') -split "`n" | Where-Object { $_ -match 'defaultSmsSubId' } | Select-Object -First 1).Trim()
}
foreach ($k in $info.Keys) { $relatorio.Add("${k}: $($info[$k])") }
$relatorio | Write-Output
if ($info['ABI'] -notmatch 'arm64-v8a|x86_64') { throw 'O APK só tem bibliotecas arm64-v8a e x86_64.' }
if ($info['Estado dos chips'] -notmatch 'LOADED') { Write-Warning 'Nenhum chip com estado LOADED: o SMS vai falhar.' }
if ($info['Modo avião'] -eq '1') { Write-Warning 'Modo avião ligado: o SMS vai falhar.' }
if ($SoDiagnostico) { return }

# 5. Envio real exige autorização explícita do dono do número.
if (-not $Numero) { throw 'Informe -Numero +55DDDNUMERO (celular brasileiro: 9 dígitos após o DDD).' }
if ($Numero -notmatch '^\+?\d{10,15}$') { throw 'Número inválido. Use só dígitos, com +55 e DDD.' }
if ($Numero -match '^\+?55(\d{2})(\d+)$' -and $Matches[2].Length -ne 9) { Write-Warning "Depois do DDD há $($Matches[2].Length) dígitos; celular no Brasil tem 9." }
if (-not $Confirmado) { throw "Envio real NÃO feito. Rode de novo com -Confirmado somente se o dono de $(Mascarar $Numero) autorizou o SMS de teste." }

# 6. Instala app e testes e concede permissões.
$apk = Join-Path $kit 'Camaleao-SOS-debug.apk'
$testes = Join-Path $kit 'Camaleao-SOS-testes.apk'
foreach ($f in $apk, $testes) { if (-not (Test-Path $f)) { throw "Arquivo do kit ausente: $f" } }
& $adb -s $Serial install -r $apk | Out-Null; if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar o app.' }
& $adb -s $Serial install -r $testes | Out-Null; if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar os testes.' }
foreach ($p in 'SEND_SMS', 'ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION', 'RECORD_AUDIO', 'POST_NOTIFICATIONS') {
    & $adb -s $Serial shell pm grant $pkg "android.permission.$p" 2>$null | Out-Null
}

# 7. Executa o teste: SMS de alerta e, em seguida, um alerta cancelado que NÃO pode enviar.
Write-Output 'Mantenha o celular DESBLOQUEADO: a localização exige o app visível. O teste leva ~40 s.'
& $adb -s $Serial shell input keyevent KEYCODE_WAKEUP | Out-Null
& $adb -s $Serial logcat -c
$instr = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'smsTo', $Numero, '-e', 'class', $teste)
if ($SemLocalizacao) { $instr += @('-e', 'smsLocation', 'false') }
$instr += $runner
$saida = (& $adb @instr 2>&1 | Out-String)
$passou = $saida -match 'OK \(1 test\)'
$app = (& $adb -s $Serial logcat -d -s CamaleaoTest:I 2>&1 | Select-String 'SMS para' | Select-Object -Last 1)
$radio = (& $adb -s $Serial logcat -d -b main,radio 2>&1 | Select-String -Pattern 'onSendSmsResponse|SEND_SMS error|Failure Reason|sendMultipartText|SMS_SEND_FAIL' | Select-Object -Last 8)
$falha = ($saida -split "`n" | Where-Object { $_ -match 'stack=' } | Select-Object -First 1)

$relatorio.Add('')
$relatorio.Add("Resultado: $(if ($passou) { 'SMS ACEITO PELA OPERADORA; alerta cancelado não enviou' } else { 'FALHOU' })")
if ($app) { $relatorio.Add("App: $(Mascarar $app.Line)") }
if ($falha) { $relatorio.Add("Teste: $(Mascarar $falha.Trim())") }
if ($radio) { $relatorio.Add('Sistema/rádio:'); $radio | ForEach-Object { $relatorio.Add('  ' + (Mascarar $_.Line)) } }

# 8. Limpa os dados de teste (contato, consentimentos) do app no aparelho.
if (-not $Manter) { & $adb -s $Serial shell pm clear $pkg | Out-Null; $relatorio.Add('Dados de teste do app apagados do aparelho.') }

$arquivo = Join-Path $kit ("relatorio-sms-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')
$relatorio | Set-Content -Path $arquivo -Encoding utf8
$relatorio | Select-Object -Skip $info.Count | Write-Output
Write-Output "Relatório (sem telefone nem coordenadas em claro): $arquivo"
if (-not $passou) { exit 1 }
