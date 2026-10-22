# Gera artifacts/Camaleao-kit-teste-sms.zip: APKs atuais + tools/kit-sms. Sem segredos.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot
$artifacts = Join-Path $projectRoot 'artifacts'
$stage = Join-Path $artifacts 'kit-teste-sms'
Push-Location $projectRoot
try {
    & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw 'Falha na compilação dos APKs.' }
    if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
    New-Item -ItemType Directory -Force $stage | Out-Null
    Copy-Item 'tools/kit-sms/*' $stage
    Copy-Item 'app/build/outputs/apk/debug/app-debug.apk' (Join-Path $stage 'Camaleao-SOS-debug.apk')
    Copy-Item 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk' (Join-Path $stage 'Camaleao-SOS-testes.apk')
    Get-ChildItem $stage -File | ForEach-Object {
        "$((Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant())  $($_.Name)"
    } | Set-Content (Join-Path $stage 'SHA256SUMS.txt') -Encoding ascii
    $zip = Join-Path $artifacts 'Camaleao-kit-teste-sms.zip'
    Compress-Archive -Path "$stage\*" -DestinationPath $zip -Force
    Get-Item $zip | Select-Object Name, @{n = 'MB'; e = { [math]::Round($_.Length / 1MB, 1) } }
} finally { Pop-Location }
