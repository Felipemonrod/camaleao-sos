"""Baixa o modelo aprovado, verifica SHA-256 e extrai somente dentro de assets."""
from pathlib import Path
import hashlib
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
URL = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip"
SHA256 = "6e1ce909032e1afa7a88e68a3d628ecafff302bdf195befab308826c395e93b7"
archive = ROOT / ".tools" / "vosk-model-small-pt-0.3.zip"
destination = ROOT / "app/src/main/assets/models/vosk-pt"
archive.parent.mkdir(parents=True, exist_ok=True)
if not archive.exists():
    urllib.request.urlretrieve(URL, archive)
if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
    raise SystemExit("Modelo recusado: SHA-256 diferente do artefato validado.")
destination.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(archive) as bundle:
    if sum(item.file_size for item in bundle.infolist()) > 200_000_000:
        raise SystemExit("Pacote maior que o limite esperado.")
    for item in bundle.infolist():
        parts = Path(item.filename).parts
        if not parts or parts[0] != "vosk-model-small-pt-0.3":
            raise SystemExit("Caminho inesperado no pacote.")
        output = destination.joinpath(*parts[1:]).resolve()
        if not output.is_relative_to(destination.resolve()):
            raise SystemExit("Caminho fora de assets recusado.")
        if item.is_dir():
            output.mkdir(parents=True, exist_ok=True)
        else:
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(bundle.read(item))
print("Modelo PT validado e preparado. Licença registrada em docs/06.")
