import struct
import sys
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
WORLD = ROOT / "testserver" / "world" / "dimensions" / "minecraft" / "overworld" / "region"
OUT = ROOT / "renders"
MIN_Y = -64


def nbt(buf, i, tag):
    if tag == 1: return struct.unpack_from(">b", buf, i)[0], i + 1
    if tag == 2: return struct.unpack_from(">h", buf, i)[0], i + 2
    if tag == 3: return struct.unpack_from(">i", buf, i)[0], i + 4
    if tag == 4: return struct.unpack_from(">q", buf, i)[0], i + 8
    if tag == 5: return struct.unpack_from(">f", buf, i)[0], i + 4
    if tag == 6: return struct.unpack_from(">d", buf, i)[0], i + 8
    if tag == 7:
        n = struct.unpack_from(">i", buf, i)[0]; return buf[i + 4:i + 4 + n], i + 4 + n
    if tag == 8:
        n = struct.unpack_from(">H", buf, i)[0]; return buf[i + 2:i + 2 + n].decode("utf-8", "replace"), i + 2 + n
    if tag == 9:
        t = buf[i]; n = struct.unpack_from(">i", buf, i + 1)[0]; i += 5; out = []
        for _ in range(n):
            v, i = nbt(buf, i, t); out.append(v)
        return out, i
    if tag == 10:
        out = {}
        while True:
            t = buf[i]; i += 1
            if t == 0: return out, i
            n = struct.unpack_from(">H", buf, i)[0]; name = buf[i + 2:i + 2 + n].decode("utf-8", "replace"); i += 2 + n
            out[name], i = nbt(buf, i, t)
    if tag == 11:
        n = struct.unpack_from(">i", buf, i)[0]; return np.frombuffer(buf, ">i4", n, i + 4), i + 4 + 4 * n
    if tag == 12:
        n = struct.unpack_from(">i", buf, i)[0]; return np.frombuffer(buf, ">i8", n, i + 4), i + 4 + 8 * n
    raise ValueError(tag)


def chunks(path):
    data = path.read_bytes()
    for k in range(1024):
        off = int.from_bytes(data[k * 4:k * 4 + 3], "big")
        if not off: continue
        p = off * 4096
        ln = struct.unpack_from(">i", data, p)[0]
        raw = zlib.decompress(data[p + 5:p + 4 + ln])
        root, _ = nbt(raw, 3 + struct.unpack_from(">H", raw, 1)[0], 10)
        yield root


def section_blocks(sec):
    bs = sec.get("block_states")
    if not bs: return None
    pal = [p["Name"] for p in bs["palette"]]
    if "data" not in bs: return np.zeros(4096, np.int32), pal
    bits = max(4, (len(pal) - 1).bit_length())
    per = 64 // bits
    longs = bs["data"].astype(np.uint64)
    idx = np.arange(4096)
    words = longs[idx // per]
    vals = (words >> ((idx % per) * bits).astype(np.uint64)) & np.uint64((1 << bits) - 1)
    return vals.astype(np.int32), pal


COLORS = {
    "air": None, "cave_air": None, "void_air": None,
    "water": (40, 90, 200), "lava": (230, 90, 20),
    "grass_block": (90, 160, 60), "dirt": (130, 95, 60), "coarse_dirt": (120, 85, 55), "podzol": (110, 80, 40),
    "stone": (125, 125, 125), "deepslate": (70, 70, 75), "andesite": (135, 135, 135), "granite": (150, 105, 90),
    "diorite": (190, 190, 190), "tuff": (100, 100, 90), "gravel": (140, 130, 125), "sand": (220, 205, 150),
    "sandstone": (210, 195, 140), "snow_block": (245, 245, 250), "snow": (240, 240, 250), "ice": (160, 190, 240),
    "bedrock": (30, 30, 30), "calcite": (225, 225, 220), "moss_block": (80, 120, 40), "clay": (160, 165, 175),
    "mud": (60, 55, 50), "packed_ice": (140, 170, 230), "powder_snow": (235, 240, 250), "red_sand": (190, 100, 40),
    "terracotta": (150, 90, 65), "mycelium": (120, 100, 120), "pink_petals": (240, 170, 200),
}


def color(name):
    n = name.split(":")[1]
    if n in COLORS: return COLORS[n]
    if "leaves" in n: return (50, 120, 40)
    if "log" in n or "wood" in n: return (100, 75, 45)
    if "ore" in n: return (125, 125, 125)
    if "terracotta" in n: return (150, 90, 65)
    if "water" in n or "kelp" in n or "seagrass" in n: return (40, 90, 200)
    return (110, 150, 80)


TERRAIN_WORDS = ["stone", "dirt", "grass_block", "podzol", "sand", "gravel", "snow_block", "clay", "deepslate",
                 "andesite", "granite", "diorite", "tuff", "calcite", "moss_block", "mud", "terracotta", "ice", "ore", "water"]


def load_world():
    cols = {}
    for f in WORLD.glob("*.mca"):
        for c in chunks(f):
            if c.get("Status") not in ("minecraft:full",): continue
            cx, cz = c["xPos"], c["zPos"]
            vol = {}
            for sec in c["sections"]:
                sb = section_blocks(sec)
                if sb: vol[sec["Y"]] = sb
            cols[(cx, cz)] = vol
    return cols


def build(cols, height):
    xs = [k[0] for k in cols]; zs = [k[1] for k in cols]
    x0, z0 = min(xs), min(zs)
    W, D = (max(xs) - x0 + 1) * 16, (max(zs) - z0 + 1) * 16
    names = ["minecraft:air"]
    ids = {"minecraft:air": 0}
    vol = np.zeros((height, D, W), np.uint16)
    for (cx, cz), secs in cols.items():
        for sy, (vals, pal) in secs.items():
            gmap = np.array([ids.setdefault(p, len(ids)) for p in pal], np.uint16)
            for p in pal:
                if p not in names: names.append(p)
            y0 = sy * 16 - MIN_Y
            if y0 < 0 or y0 + 16 > height: continue
            vol[y0:y0 + 16, (cz - z0) * 16:(cz - z0 + 1) * 16, (cx - x0) * 16:(cx - x0 + 1) * 16] = gmap[vals].reshape(16, 16, 16)
    inv = {v: k for k, v in ids.items()}
    global TERRAIN, WATER
    TERRAIN = np.array([any(t in inv[i] for t in TERRAIN_WORDS) for i in range(len(ids))])
    WATER = np.array([inv[i] == "minecraft:water" for i in range(len(ids))])
    lut = np.zeros((len(ids), 3), np.uint8); solid = np.zeros(len(ids), bool)
    for i, n in inv.items():
        c = color(n)
        if c is not None:
            lut[i] = c; solid[i] = True
    return vol, lut, solid, (x0 * 16, z0 * 16)


def top_view(vol, lut, solid, y_lo=0, y_hi=None):
    sub = vol[y_lo:y_hi]
    s = solid[sub]
    has = s.any(axis=0)
    top = sub.shape[0] - 1 - np.argmax(s[::-1], axis=0)
    blk = np.take_along_axis(sub, top[None], 0)[0]
    img = lut[blk].astype(np.float32)
    h = (top + y_lo + MIN_Y).astype(np.float32)
    shade = np.clip(0.55 + (h - 40) / 300, 0.45, 1.25)
    img = img * shade[..., None]
    img[~has] = (15, 15, 25)
    return np.clip(img, 0, 255).astype(np.uint8), np.where(has, top + y_lo + MIN_Y, -999)


def main():
    height = int(sys.argv[1]) if len(sys.argv) > 1 else 576
    OUT.mkdir(exist_ok=True)
    vol, lut, solid, origin = build(load_world(), height)
    print("volume", vol.shape, "origin", origin)
    full, hm = top_view(vol, lut, solid)
    Image.fromarray(full).resize((full.shape[1] * 2, full.shape[0] * 2), Image.NEAREST).save(OUT / "top_all.png")
    ground, ghm = top_view(vol, lut, solid, 0, 300 - MIN_Y)
    Image.fromarray(ground).resize((ground.shape[1] * 2, ground.shape[0] * 2), Image.NEAREST).save(OUT / "top_ground.png")
    for z in (vol.shape[1] // 4, vol.shape[1] // 2, 3 * vol.shape[1] // 4):
        sl = vol[:, z, :]
        img = lut[sl].copy()
        img[~solid[sl]] = (170, 200, 240)
        img = img[::-1]
        Image.fromarray(img).resize((img.shape[1] * 2, img.shape[0] * 2), Image.NEAREST).save(OUT / f"slice_z{z + origin[1]}.png")
    for name, lo, hi in (("ground", 0, 300), ("giant", 200, 305), ("dense", 305, 385), ("sparse", 385, 464)):
        s = solid[vol[lo - MIN_Y:hi - MIN_Y]].any(axis=0)
        print(f"{name}: coverage {s.mean() * 100:.1f}%")
    sub = vol[:320 - MIN_Y]; t = TERRAIN[sub]
    hm = np.where(t.any(0), sub.shape[0] - 1 - np.argmax(t[::-1], axis=0) + MIN_Y, -999)
    height_image(hm, OUT / "height.png")
    print("columns with water above y=64: %.1f%%" % (WATER[vol[65 - MIN_Y:320 - MIN_Y]].any(0).mean() * 100))
    g = ghm[ghm > -999]
    print("ground height: min", g.min(), "median", int(np.median(g)), "max", g.max())


def height_image(hm, path):
    h = hm.astype(np.float32)
    v = np.clip((h - 40) / 220, 0, 1)
    img = np.stack([v * 255, np.clip(1.4 * v, 0, 1) * 200 + 30 * (1 - v), (1 - v) * 160 + 40], -1)
    img[hm <= 63] = (20, 40, 120)
    gy, gx = np.gradient(h)
    shade = np.clip(1 - 0.08 * gx, 0.5, 1.3)
    img = np.clip(img * shade[..., None], 0, 255).astype(np.uint8)
    Image.fromarray(img).save(path)


if __name__ == "__main__":
    main()
