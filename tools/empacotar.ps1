$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot
$stage = Join-Path (Split-Path $projectRoot) '.tools/package-v02/CamaleaoSOS'
$artifacts = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Force $stage,$artifacts | Out-Null
Push-Location $projectRoot
try {
    $files = & git ls-files --cached --others --exclude-standard
    foreach ($relative in $files) {
        if ($relative -match '(^|/)(data|certs)/|\.keystore$|\.jks$|local\.properties$') { throw "Arquivo privado inesperado: $relative" }
        $source = Join-Path $projectRoot $relative
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { continue }
        $destination = Join-Path $stage $relative
        New-Item -ItemType Directory -Force (Split-Path $destination) | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination -Force
    }
    New-Item -ItemType Directory -Force (Join-Path $stage 'app/src/main/assets/models'),(Join-Path $stage 'app/src/androidTest/assets'),(Join-Path $stage 'artifacts') | Out-Null
    Copy-Item -LiteralPath 'app/src/main/assets/models/vosk-pt' -Destination (Join-Path $stage 'app/src/main/assets/models') -Recurse -Force
    Get-ChildItem 'app/src/androidTest/assets' -Filter '*.wav' | Copy-Item -Destination (Join-Path $stage 'app/src/androidTest/assets') -Force
    foreach ($name in @('final-build.txt','device-build.txt','device-tests-final.txt','server-tests.txt','sot.txt','lint.html','apk-verification.txt','boot-notifications.txt','boot-services.txt')) {
        $source = Join-Path $artifacts $name
        if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination (Join-Path $stage 'artifacts') -Force }
    }
    Copy-Item -LiteralPath (Join-Path $artifacts 'screenshots') -Destination (Join-Path $stage 'artifacts') -Recurse -Force
    Copy-Item 'app/build/outputs/apk/debug/app-debug.apk' (Join-Path $artifacts 'Camaleao-SOS-v0.2-debug.apk') -Force
    Compress-Archive -LiteralPath $stage -DestinationPath (Join-Path $artifacts 'Camaleao-SOS-v0.2-fontes.zip') -Force
    Get-Item (Join-Path $artifacts 'Camaleao-SOS-v0.2-debug.apk'),(Join-Path $artifacts 'Camaleao-SOS-v0.2-fontes.zip') | ForEach-Object {
        [pscustomobject]@{Arquivo=$_.Name;Bytes=$_.Length;SHA256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
    } | Export-Csv (Join-Path $artifacts 'SHA256SUMS.csv') -NoTypeInformation -Encoding utf8
    Write-Output 'Pacote criado sem certificados, tokens, dados do servidor ou configurações locais.'
} finally { Pop-Location }
