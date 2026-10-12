"""Create the portable Android Studio source archive and copy the tested debug APK."""
from pathlib import Path
import hashlib
import shutil
import zipfile

project = Path(__file__).resolve().parents[1]
output = project.parent
version = "1.12.0"
source_apk = project / "app/build/outputs/apk/debug/app-debug.apk"
if not source_apk.is_file():
    raise SystemExit("Compile :app:assembleDebug before packaging.")
archive = output / f"MisFinanzas-{version}-AndroidStudio.zip"
apk = output / f"MisFinanzas-{version}-debug.apk"
excluded_dirs = {".git", ".gradle", ".kotlin", ".idea", "build", "__pycache__"}
excluded_names = {"local.properties", ".DS_Store"}
files = sorted(p for p in project.rglob("*") if p.is_file()
               and not set(p.relative_to(project).parts).intersection(excluded_dirs)
               and p.name not in excluded_names and p.suffix not in {".log", ".apk", ".jks", ".keystore"})
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
    for p in files:
        z.write(p, f"MisFinanzas-{version}/{p.relative_to(project).as_posix()}")
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None, "Archive failed integrity check"
    names = set(z.namelist())
    for required in ("settings.gradle.kts", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "app/src/main/AndroidManifest.xml"):
        assert f"MisFinanzas-{version}/{required}" in names, required
    assert all("local.properties" not in name for name in names)
shutil.copy2(source_apk, apk)
checksums = "".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in (archive, apk))
(output / f"MisFinanzas-{version}-SHA256.txt").write_text(checksums, encoding="utf-8")
print(f"Source files: {len(files)}")
for p in (archive, apk):
    print(f"{p.name}: {p.stat().st_size:,} bytes")
