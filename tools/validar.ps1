param([string]$Serial='', [switch]$AlternarRedeEmulador)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot
Push-Location $projectRoot
try {
    & .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
    if ($LASTEXITCODE -ne 0) { throw 'Falha na compilação, testes ou Lint.' }
    & node --test server/test/*.test.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Falha nos testes do servidor; execute node server/setup.mjs primeiro.' }
    if ($Serial) {
        if ($AlternarRedeEmulador -and $Serial -notmatch '^emulator-') { throw 'Alternância de rede permitida somente em emulador dedicado.' }
        & .\gradlew.bat :app:assembleDebugAndroidTest
        if ($LASTEXITCODE -ne 0) { throw 'Falha na compilação dos testes.' }
        & adb -s $Serial install -r app/build/outputs/apk/debug/app-debug.apk
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar aplicativo.' }
        & adb -s $Serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar testes.' }
        & adb -s $Serial shell pm grant br.com.camaleao.sos android.permission.RECORD_AUDIO
        $api=& adb -s $Serial shell getprop ro.build.version.sdk
        if ([int]$api -ge 33) { & adb -s $Serial shell pm grant br.com.camaleao.sos android.permission.POST_NOTIFICATIONS }
        $pairing=(Get-Content server/data/pareamento.txt -Raw).Trim()
        $arguments=@('-s',$Serial,'shell','am','instrument','-w','-r','-e','pairing',$pairing)
        if ($AlternarRedeEmulador) { $arguments+=@('-e','toggleNetwork','true') }
        $arguments+='br.com.camaleao.sos.test/androidx.test.runner.AndroidJUnitRunner'
        $result=& adb @arguments
        $result | Write-Output
        if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \([0-9]+ tests?\)') { throw 'Falha nos testes instrumentados.' }
    }
} finally { Pop-Location }
