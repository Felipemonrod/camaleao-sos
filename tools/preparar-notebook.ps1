<#
Prepara o notebook da apresentação para rodar o Painel local. Não compila o app.

  .\tools\preparar-notebook.ps1                  # confere tudo e prepara o Painel
  .\tools\preparar-notebook.ps1 -Ip 192.168.43.10 -Renovar   # rede nova (ex.: hotspot)

Use a mesma rede no ensaio e na apresentação: trocar de rede exige -Renovar e
parear o celular de novo.
#>
param([string]$Ip = '', [switch]$Renovar)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$ok = $true
function Check([string]$nome, [bool]$passou, [string]$dica) {
    if ($passou) { Write-Output "  [ok]    $nome" } else { Write-Output "  [falta] $nome — $dica"; $script:ok = $false }
}

Write-Output 'Ferramentas:'
$node = Get-Command node -ErrorAction SilentlyContinue
$nodeVersion = if ($node) { [int]((& node --version).TrimStart('v').Split('.')[0]) } else { 0 }
Check 'Node.js 20+' ($nodeVersion -ge 20) 'instale a versão LTS em https://nodejs.org'
$openssl = if ($env:OPENSSL) { $env:OPENSSL } else { 'C:/Program Files/Git/usr/bin/openssl.exe' }
Check 'OpenSSL (vem com o Git for Windows)' (Test-Path $openssl) 'instale o Git for Windows ou defina $env:OPENSSL'
$adb = @((Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1), "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe") | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
Check 'adb (opcional: instalar APK e ver logs)' ([bool]$adb) 'SDK Platform-Tools em https://developer.android.com/tools/releases/platform-tools'
$apk = Get-ChildItem (Join-Path $root 'artifacts') -Filter 'Camaleao-SOS*.apk' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Check 'APK do app em artifacts/' ([bool]$apk) 'copie o APK final para artifacts/ ou instale no celular antes'
if (-not $ok -and -not $node) { throw 'Sem Node.js não há Painel. Instale e rode de novo.' }

Write-Output "`nPainel:"
$setup = @((Join-Path $root 'server/setup.mjs'))
if ($Ip) { $setup += @('--ip', $Ip) }
if ($Renovar) { $setup += '--renovar' }
$saida = & node @setup
$saida | Where-Object { $_ -notmatch '^(Acesso ao Painel|Código de pareamento|eyJ)' } | Write-Output
Write-Output '  (acesso e código de pareamento: server/data/config.json e server/data/pareamento.txt; não exiba no projetor)'

Write-Output "`nFirewall do Windows (porta 8443):"
$regra = Get-NetFirewallRule -DisplayName 'Camaleao Painel 8443' -ErrorAction SilentlyContinue
if ($regra) { Write-Output '  [ok]    regra "Camaleao Painel 8443" existe' }
else {
    Write-Output '  [falta] rode UMA vez num PowerShell como Administrador:'
    Write-Output "          New-NetFirewallRule -DisplayName 'Camaleao Painel 8443' -Direction Inbound -Protocol TCP -LocalPort 8443 -Action Allow -Profile Private"
    Write-Output '          e marque a rede (hotspot) como Privada nas configurações de Wi-Fi.'
}

Write-Output @"

Roteiro do dia:
  1. Celular com hotspot ligado; notebook conectado nele (rede Privada).
  2. Se a rede for nova:  .\tools\preparar-notebook.ps1 -Ip <IP do notebook no hotspot> -Renovar
  3. cd server; npm start   (deixe a janela aberta)
  4. No celular: Área protegida > Rede > colar o código de server/data/pareamento.txt
  5. Abra https://<IP>:8443 no navegador, confira o fingerprint e entre com o acesso.
  6. Envie um teste identificado ao Painel antes de começar a apresentação.
"@
