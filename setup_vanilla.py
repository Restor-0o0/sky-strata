import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

MC = "1.20.1"
FORGE = "47.4.28"
ROOT = Path(__file__).parent
REF = ROOT / "vanilla"
SERVER = ROOT / "testserver"
JAVA = os.environ.get("JAVA", "java")


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "sky-strata-setup"})) as r:
        return r.read()


def extract(jar, prefix, dst):
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if n.startswith(prefix) and not n.endswith("/"):
                p = dst / n
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_bytes(z.read(n))


def main():
    manifest = json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
    version = json.loads(get(next(v["url"] for v in manifest["versions"] if v["id"] == MC)))
    SERVER.mkdir(exist_ok=True)
    jar = SERVER / "server.jar"
    if not jar.exists():
        print("downloading server", MC)
        jar.write_bytes(get(version["downloads"]["server"]["url"]))

    shutil.rmtree(REF, ignore_errors=True)
    REF.mkdir()
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run([JAVA, "-DbundlerMainClass=net.minecraft.data.Main", "-jar", str(jar.resolve()),
                        "--reports", "--output", tmp], cwd=SERVER, check=True)
        shutil.copy(Path(tmp) / "reports" / "biome_parameters" / "minecraft" / "overworld.json",
                    REF / "biome_parameters_overworld.json")

    inner = SERVER / "versions" / MC / f"server-{MC}.jar"
    extract(inner, "data/minecraft/", REF)
    (REF / "lang").mkdir()
    with zipfile.ZipFile(inner) as z:
        (REF / "lang" / "en_us.json").write_bytes(z.read("assets/minecraft/lang/en_us.json"))
    index = json.loads(get(version["assetIndex"]["url"]))
    for code in sorted(p.stem for p in (ROOT / "lang").glob("*.json") if p.stem != "en_us"):
        h = index["objects"][f"minecraft/lang/{code}.json"]["hash"]
        (REF / "lang" / f"{code}.json").write_bytes(get(f"https://resources.download.minecraft.net/{h[:2]}/{h}"))

    print("downloading forge", FORGE)
    jar = get(f"https://maven.minecraftforge.net/net/minecraftforge/forge/{MC}-{FORGE}/forge-{MC}-{FORGE}-universal.jar")
    extract(io.BytesIO(jar), "data/forge/tags/worldgen/biome/", REF / "loader")
    print("reference data ready in", REF, file=sys.stderr)


if __name__ == "__main__":
    main()
