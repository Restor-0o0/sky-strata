import os
import subprocess
import sys
import threading
import time
from pathlib import Path

JAVA = os.environ.get("JAVA", "java")
DIR = Path(__file__).resolve().parent.parent / "testserver"
RADIUS_CHUNKS = int(sys.argv[1]) if len(sys.argv) > 1 else 16
CX = int(sys.argv[2]) if len(sys.argv) > 2 else 0
CZ = int(sys.argv[3]) if len(sys.argv) > 3 else 0

neo = sorted(DIR.glob("libraries/net/neoforged/neoforge/*/" + ("win_args.txt" if os.name == "nt" else "unix_args.txt")))
launch = ["@" + neo[-1].relative_to(DIR).as_posix()] if neo else ["-jar", "server.jar"]
proc = subprocess.Popen([JAVA, "-Xmx6G", *launch, "nogui"], cwd=DIR,
                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                        text=True, encoding="utf-8", errors="replace", bufsize=1)
lines = []
log = open(DIR / "driver.log", "w", encoding="utf-8")


def reader():
    for line in proc.stdout:
        lines.append(line)
        log.write(line)
        log.flush()


threading.Thread(target=reader, daemon=True).start()


def send(cmd):
    proc.stdin.write(cmd + "\n")
    proc.stdin.flush()


def wait_for(text, timeout=600, start=0):
    t = time.time()
    while time.time() - t < timeout:
        for l in lines[start:]:
            if text in l:
                return True
        if proc.poll() is not None:
            return False
        time.sleep(0.5)
    return False


t0 = time.time()
if not wait_for("Done (", 900):
    print("server failed to start", file=sys.stderr)
    print("".join(lines[-60:]))
    sys.exit(1)
print(f"startup {time.time() - t0:.1f}s")

r = RADIUS_CHUNKS
blocks = []
for bx in range(CX - r, CX + r, 16):
    for bz in range(CZ - r, CZ + r, 16):
        x0, z0, x1, z1 = bx * 16, bz * 16, (bx + 15) * 16, (bz + 15) * 16
        send(f"forceload add {x0} {z0} {x1} {z1}")
        blocks.append((x0, z0, x1, z1))

t1 = time.time()
pending = list(blocks)
while pending:
    time.sleep(2)
    nxt = []
    for x0, z0, x1, z1 in pending:
        mark = len(lines)
        pts = [(x0, z0), (x1, z1), (x0, z1), (x1, z0), ((x0 + x1) // 2, (z0 + z1) // 2)]
        cond = " ".join(f"if loaded {x} 0 {z}" for x, z in pts)
        send(f"execute {cond} run say READY {x0} {z0}")
        time.sleep(0.3)
        if not any(f"READY {x0} {z0}" in l for l in lines[mark:]):
            nxt.append((x0, z0, x1, z1))
    pending = nxt
    if time.time() - t1 > 1800:
        break
gen = time.time() - t1
chunks = (2 * r) ** 2
print(f"generated {chunks} chunks in {gen:.1f}s -> {chunks / gen:.1f} chunks/s")
send("save-all flush")
wait_for("Saved the game", 300, len(lines) - 5)
send("stop")
proc.wait(120)
errs = [l for l in lines if "ERROR" in l or "Exception" in l or "WARN" in l]
print("warn/err lines:", len(errs))
print("".join(errs[:30]))
