# Amostras sintéticas locais; não são medição de precisão com pessoas reais.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$destination = Join-Path (Split-Path $PSScriptRoot) 'app/src/androidTest/assets'
New-Item -ItemType Directory -Force $destination | Out-Null
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $voice = $synth.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -eq 'pt-BR' } | Select-Object -First 1
    if (-not $voice) { throw 'Instale uma voz de texto para fala pt-BR no Windows para gerar as amostras.' }
    $synth.SelectVoice($voice.VoiceInfo.Name)
    $format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000,[System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,[System.Speech.AudioFormat.AudioChannel]::Mono)
    foreach ($clip in @(@{Name='frase';Text='Preciso buscar meu caderno azul'},@{Name='negativo';Text='Vou buscar meu caderno azul'},@{Name='comando';Text='Enviar alerta'})) {
        $synth.SetOutputToWaveFile((Join-Path $destination ($clip.Name + '.wav')), $format)
        $synth.Speak($clip.Text)
    }
} finally { $synth.Dispose() }
