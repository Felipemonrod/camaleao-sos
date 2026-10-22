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
        & adb -s $Serial shell pm grant br.com.camaleao.sos android.permission.SEND_SMS
        & adb -s $Serial shell pm grant br.com.camaleao.sos android.permission.ACCESS_FINE_LOCATION
        & adb -s $Serial shell pm grant br.com.camaleao.sos android.permission.ACCESS_COARSE_LOCATION
        # Posição simulada (Praça da Sé) para os testes de localização; só existe em emulador.
        if ($Serial -match '^emulator-') { & adb -s $Serial emu geo fix -46.63331 -23.55052 | Out-Null }
        $api=& adb -s $Serial shell getprop ro.build.version.sdk
        if ([int]$api -ge 33) { & adb -s $Serial shell pm grant br.com.camaleao.sos android.permission.POST_NOTIFICATIONS }
        $pairing=(Get-Content server/data/pareamento.txt -Raw).Trim()
        & adb -s $Serial shell input keyevent KEYCODE_WAKEUP | Out-Null
        if ((& adb -s $Serial shell dumpsys window) -match 'isKeyguardShowing=true') { throw 'Celular bloqueado: desbloqueie a tela e rode de novo (os testes abrem o app).' }
        $arguments=@('-s',$Serial,'shell','am','instrument','-w','-r','-e','pairing',$pairing)
        $arguments+=@('-e','notClass','br.com.camaleao.sos.ScreenOffTest')
        if ($Serial -notmatch '^emulator-') {
            # Aparelho físico alcança o Painel do notebook pelo cabo, sem depender do Wi-Fi.
            & adb -s $Serial reverse tcp:8443 tcp:8443 | Out-Null
            $arguments+=@('-e','panelUrl','https://127.0.0.1:8443')
        }
        if ($AlternarRedeEmulador) { $arguments+=@('-e','toggleNetwork','true') }
        $arguments+='br.com.camaleao.sos.test/androidx.test.runner.AndroidJUnitRunner'
        $result=& adb @arguments
        $result | Write-Output
        if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \([0-9]+ tests?\)') { throw 'Falha nos testes instrumentados.' }
        # Por último: testes que apagam a tela e podem deixar o aparelho bloqueado.
        $screen=& adb -s $Serial shell am instrument -w -r -e class br.com.camaleao.sos.ScreenOffTest br.com.camaleao.sos.test/androidx.test.runner.AndroidJUnitRunner
        $screen | Write-Output
        if ($LASTEXITCODE -ne 0 -or ($screen -join "`n") -notmatch 'OK \([0-9]+ tests?\)') { throw 'Falha nos testes de tela apagada.' }
    }
} finally { Pop-Location }
