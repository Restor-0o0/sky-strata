import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import render


def nib(a):
    if a is None or len(a) == 0: return None
    b = np.frombuffer(bytes(a), np.uint8)
    return np.stack([b & 15, b >> 4], -1).reshape(16, 16, 16)


def main():
    walls, floors, lights, chunks = [], [], 0, 0
    for f in render.WORLD.glob("*.mca"):
        for c in render.chunks(f):
            if c.get("Status") != "minecraft:full": continue
            chunks += 1
            for s in c["sections"]:
                sb = render.section_blocks(s)
                sky = nib(s.get("SkyLight"))
                if not sb or sky is None: continue
                vals, pal = sb
                lights += sum(int((vals == i).sum()) for i, p in enumerate(pal) if p == "minecraft:light")
                solid = np.array([not p.endswith("air") and p != "minecraft:light" for p in pal])
                sol = solid[vals].reshape(16, 16, 16)
                bl = nib(s.get("BlockLight"))
                eff = np.maximum(sky, bl) if bl is not None else sky
                air = ~sol
                if 12 <= s["Y"] <= 18:
                    nb = np.zeros_like(sol)
                    nb[:, :, 1:] |= sol[:, :, :-1]; nb[:, :, :-1] |= sol[:, :, 1:]
                    nb[:, 1:, :] |= sol[:, :-1, :]; nb[:, :-1, :] |= sol[:, 1:, :]
                    m = air & nb & (sky < 15)
                    walls.append(np.stack([sky[m], eff[m]]))
                below = np.zeros_like(sol); below[1:] = sol[:-1]
                m = air & below & (sky < 8)
                floors.append(np.stack([sky[m], eff[m]]))
    print(f"chunks {chunks}, light blocks {lights} ({lights / max(chunks, 1):.1f}/chunk)")
    w = np.concatenate(walls, 1)
    print(f"shaded walls y 192..303: dark (<8) by sky {(w[0] < 8).mean():.0%}, with light blocks {(w[1] < 8).mean():.0%}")
    fl = np.concatenate(floors, 1)
    print(f"floors in deep shade: lit to >=8 {(fl[1] >= 8).mean():.0%}")


if __name__ == "__main__":
    main()
