import json
import math
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).parent
REF = ROOT / "vanilla"
VAN = REF / "data" / "minecraft"
OUT = ROOT / "src" / "generated" / "resources"

NS = "skystrata"
PRESET = "sky_strata"

MIN_Y = -64
HEIGHTS = (528, 784, 1040, 1552, 2096)
TERRAIN_TOP_SLIDE = (130, 175)
LAND_LIFT = 0.20
DRY_ABOVE_Y = 10
AMBIENT_LIGHT = 0.1
VANILLA_LIGHT = "_vanilla_light"

SKY_BIOMES = {
    "sky_taiga": ("taiga", (-2.0, -0.6), (-2.0, 2.0)),
    "sky_meadow": ("meadow", (-0.6, -0.2), (-2.0, 0.0)),
    "sky_forest": ("forest", (-0.6, -0.2), (0.0, 2.0)),
    "sky_cherry": ("cherry_grove", (-0.2, 0.2), (-2.0, 2.0)),
    "sky_birch": ("birch_forest", (0.2, 0.6), (-2.0, 2.0)),
    "sky_savanna": ("savanna", (0.6, 2.0), (-2.0, 2.0)),
}
SKY_DEPTH = -2.0
LICHEN_PER_BLOCK = 0.3


def load(rel):
    return json.loads((VAN / rel).read_text(encoding="utf-8"))


def write(rel, obj, ns=NS):
    p = OUT / "data" / ns / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, indent=1, ensure_ascii=False), encoding="utf-8")


def df(name):
    return f"{NS}:{name}"


def add(a, b):
    return {"type": "minecraft:add", "left": a, "right": b}


def mul(a, b):
    return {"type": "minecraft:mul", "left": a, "right": b}


def mn(a, b):
    return {"type": "minecraft:min", "left": a, "right": b}


def mx(a, b):
    return {"type": "minecraft:max", "left": a, "right": b}


def clamp(x, lo, hi):
    return {"type": "minecraft:clamp", "input": x, "min": lo, "max": hi}


def noise(n, xz=1.0, y=0.0):
    return {"type": "minecraft:noise", "noise": n, "xz_scale": xz, "y_scale": y}


def flat(x):
    return {"type": "minecraft:cache", "input": x}


def grad(fy, fv, ty, tv):
    return {"type": "minecraft:gradient", "axis": "y", "from_coordinate": fy, "from_value": fv, "to_coordinate": ty, "to_value": tv}


def yrange(lo, hi, inside, outside):
    return {"type": "minecraft:range_choice", "input": grad(-4064, -4064, 4062, 4062),
            "min_inclusive": lo, "max_exclusive": hi,
            "when_in_range": inside, "when_out_of_range": outside}


def parity_amplitude(amps):
    a = 2 ** (len(amps) - 1) / (2 ** len(amps) - 1)
    octaves = []
    for m in amps:
        if m:
            octaves.append(a * m)
        a *= 0.5
    deviation = math.sqrt(sum((0.2702247831245211 * o) ** 2 for o in octaves)) * math.sqrt(2)
    new = sum(octaves) / 3 / deviation
    nz = [i for i, m in enumerate(amps) if m]
    old = 0.5 / 3 / (0.1 * (1 + 1 / (nz[-1] - nz[0] + 1)))
    return old / new


def noises():
    defs = {
        "isl_giant": (-9, [1.0, 0.5, 0.3, 0.15]),
        "isl_giant_top": (-9, [1.0, 0.4]),
        "isl_dense": (-8, [1.0, 1.0, 0.5, 0.25]),
        "isl_dense_mask": (-10, [1.0]),
        "isl_sparse": (-5, [1.0, 0.6, 0.3]),
        "isl_detail": (-4, [1.0, 0.5]),
    }
    for name, (oct_, amps) in defs.items():
        n = {"base_amplitude": parity_amplitude(amps), "base_octave": oct_, "octave_count": len(amps)}
        if any(a != 1.0 for a in amps):
            n["amplitude_modifiers"] = amps
        write(f"worldgen/noise/{name}.json", n)


def density_functions():
    land = clamp(mul(4.0, add("minecraft:overworld/continents", 0.15)), 0.0, 1.0)
    write("worldgen/density_function/land.json", land)
    write("worldgen/density_function/offset.json", flat(add("minecraft:overworld/offset", mul(LAND_LIFT, df("land")))))
    write("worldgen/density_function/depth.json", add(grad(-64, 1.5, 320, -1.5), df("offset")))

    sc = json.dumps(load("worldgen/density_function/overworld/sloped_cheese.json"))
    write("worldgen/density_function/sloped_cheese.json",
          json.loads(sc.replace("minecraft:overworld/depth", df("depth"))))

    write("worldgen/density_function/biome_depth.json", mx(0.0, df("depth")))


def layer_count(per_block, from_ground=False):
    return {"type": f"{NS}:layer_count", "per_block": per_block, "from_ground": from_ground}


def island_lichen():
    cf = load("worldgen/feature/glow_lichen.json")
    cf.update(search_range=24, chance_of_spreading=1.0)
    cf["can_be_placed_on"] += ["minecraft:dirt", "minecraft:coarse_dirt"]
    write("worldgen/feature/island_lichen.json", cf)
    write("worldgen/placed_feature/island_lichen.json", {"feature": df("island_lichen"), "placement": [
        layer_count(LICHEN_PER_BLOCK, True),
        {"type": "minecraft:in_square"},
        {"type": "minecraft:biome"}]})
    return [df("island_lichen")]


LAYERED_PREFIXES = ("trees_", "patch_grass_", "flower_", "patch_tall_grass", "patch_large_fern", "patch_berry_",
                    "patch_sunflower", "patch_pumpkin", "forest_flowers", "birch_tall", "dark_forest_vegetation",
                    "bamboo_vegetation", "mushroom_island_vegetation", "brown_mushroom_", "red_mushroom_")


UNSAFE_DECORATORS = {"minecraft:beehive", "minecraft:cocoa"}
LOWER_LAYER_COVER = 16


class SafeCopies:
    def __init__(self):
        self.done = {}

    def ref(self, kind, value, sub):
        if not isinstance(value, str):
            return sub(value)
        key = (kind, value)
        if key not in self.done:
            self.done[key] = value
            name = value.split(":")[1]
            obj = load(f"worldgen/{kind}/{name}.json")
            new = sub(obj)
            if new != obj:
                write(f"worldgen/{kind}/layered/{name}.json", new)
                self.done[key] = df(f"layered/{name}")
        return self.done[key]

    def placed(self, pf):
        return dict(pf, feature=self.ref("feature", pf["feature"], self.configured))

    def entry(self, e):
        for key in ("chance", "weight"):
            if isinstance(e, dict) and key in e:
                inner = "feature" if key == "chance" else "data"
                return dict(e, **{inner: self.ref("placed_feature", e[inner], self.placed)})
        return self.ref("placed_feature", e, self.placed)

    def configured(self, cf):
        c = json.loads(json.dumps(cf))
        if c["type"] == "minecraft:tree":
            c["decorators"] = [d for d in c.get("decorators", []) if d["type"] not in UNSAFE_DECORATORS]
        for key in ("default", "feature_true", "feature_false", "feature"):
            if key in c:
                c[key] = self.ref("placed_feature", c[key], self.placed)
        if "features" in c:
            c["features"] = [self.entry(e) for e in c["features"]]
        return c


def layered_vegetation():
    remap = {}
    safe = SafeCopies()
    for f in sorted((VAN / "worldgen" / "placed_feature").glob("*.json")):
        if not f.stem.startswith(LAYERED_PREFIXES): continue
        pf = json.loads(f.read_text(encoding="utf-8"))
        pl = pf["placement"]
        types = [m["type"].split(":")[1] for m in pl]
        if "in_square" not in types or "heightmap" not in types: continue
        if any(t in ("noise_threshold_count", "noise_based_count") for t in types): continue
        i = types.index("in_square")
        count = 1
        if i > 0 and types[i - 1] == "count":
            count = pl[i - 1]["count"]
            del pl[i - 1]
        out = []
        for m in pl:
            t = m["type"].split(":")[1]
            if t == "in_square":
                out.append({"type": "minecraft:count_on_every_layer", "count": count})
                out.append({"type": "minecraft:surface_relative_threshold_filter", "heightmap": "WORLD_SURFACE_WG",
                            "max_inclusive": -LOWER_LAYER_COVER})
            elif t != "heightmap":
                out.append(m)
        pf["placement"] = out
        pf = safe.placed(pf)
        write(f"worldgen/placed_feature/layered/{f.stem}.json", pf)
        remap[f"minecraft:{f.stem}"] = df(f"layered/{f.stem}")
    return remap


def retarget(obj):
    s = json.dumps(obj)
    s = s.replace("minecraft:overworld/sloped_cheese", df("sloped_cheese"))
    s = s.replace("minecraft:overworld/depth", df("depth"))
    s = s.replace('"minecraft:overworld/offset"', f'"{df("offset")}"')
    s = s.replace('"from_coordinate": 240', f'"from_coordinate": {TERRAIN_TOP_SLIDE[0]}')
    s = s.replace('"to_coordinate": 256', f'"to_coordinate": {TERRAIN_TOP_SLIDE[1]}')
    return json.loads(s)


def noise_settings():
    ns = load("worldgen/noise_settings/overworld.json")
    ns["noise"]["min_y"] = MIN_Y
    ns["noise"]["height"] = HEIGHTS[0]
    r = ns["noise_router"]

    final = retarget(load("worldgen/density_function/overworld/final_density.json"))
    write("worldgen/density_function/terrain.json", yrange(MIN_Y, TERRAIN_TOP_SLIDE[1] + 8, final, -1.0))
    r["final_density"] = {"type": f"{NS}:strata", "terrain": df("terrain"), "land": df("land")}

    write("worldgen/density_function/preliminary_surface_level.json",
          retarget(load("worldgen/density_function/overworld/preliminary_surface_level.json")))
    csl = load("worldgen/density_function/overworld/chunk_surface_level.json")
    csl["input"] = df("preliminary_surface_level")
    write("worldgen/density_function/chunk_surface_level.json", csl)
    r["chunk_surface_level"] = df("chunk_surface_level")

    aq = ns["aquifers"]
    aq["fluid_level_floodedness"] = yrange(MIN_Y, DRY_ABOVE_Y, aq["fluid_level_floodedness"], -1.0)
    aq["surface_level"] = df("preliminary_surface_level")

    r["depth"] = {"type": f"{NS}:strata_biome_depth", "fallback": df("biome_depth")}
    write(f"worldgen/noise_settings/{PRESET}.json", ns)


def dimension_type():
    for island_light in (True, False):
        for i, h in enumerate(HEIGHTS):
            dt = load("dimension_type/overworld.json")
            dt.update(min_y=MIN_Y, height=h, logical_height=h, ambient_light=AMBIENT_LIGHT if island_light else 0.0)
            name = PRESET + ("" if island_light else VANILLA_LIGHT) + ("" if i == 0 else f"_{h}")
            write(f"dimension_type/{name}.json", dt)


def biome_copy(src, remap, extra):
    b = load(f"worldgen/biome/{src}.json")
    b["features"] = [[g for f in step for g in (f, remap.get(f)) if g] for step in b["features"]]
    for step, feats in extra.items():
        b["features"][step] += feats
    return b


def biomes(remap, extra):
    params = json.loads((REF / "biome_parameters_overworld.json").read_text(encoding="utf-8"))["biomes"]
    sources = {}
    for e in params:
        name = e["biome"].split(":")[1]
        e["biome"] = df(name)
        if name not in sources:
            sources[name] = name
            write(f"worldgen/biome/{name}.json", biome_copy(name, remap, extra))
    for name, (src, weird, humid) in SKY_BIOMES.items():
        b = biome_copy(src, remap, extra)
        b.setdefault("attributes", {}).update({"minecraft:visual/fog_color": "#d8e8ff", "minecraft:visual/sky_color": "#8fc4ff"})
        write(f"worldgen/biome/{name}.json", b)
        sources[name] = src
        params.append({"biome": df(name), "parameters": {
            "temperature": [-2.0, 2.0], "humidity": list(humid),
            "continentalness": [-2.0, 2.0], "erosion": [-2.0, 2.0], "weirdness": list(weird),
            "depth": SKY_DEPTH, "offset": 0.0}})
    return params, sources


def biome_tags(sources):
    by_src = {}
    for name, src in sources.items():
        by_src.setdefault(f"minecraft:{src}", []).append(name)
    n = 0
    roots = [("minecraft", VAN / "tags" / "worldgen" / "biome")]
    roots += [(d.name, d / "tags" / "worldgen" / "biome") for d in sorted((REF / "loader" / "data").iterdir())]
    for ns, base in roots:
        for f in sorted(base.rglob("*.json")):
            rel = f.relative_to(base).as_posix()
            ids = [v if isinstance(v, str) else v["id"] for v in json.loads(f.read_text(encoding="utf-8"))["values"]]
            ours = sorted({df(name) for i in ids for name in by_src.get(i, [])
                           if not (rel.startswith("has_structure/") and name in SKY_BIOMES)})
            if ours:
                write(f"tags/worldgen/biome/{rel}", {"replace": False, "values": ours}, ns=ns)
                n += 1
    return n


def world_preset(biome_params):
    preset = load("worldgen/world_preset/normal.json")
    ow = preset["dimensions"]["minecraft:overworld"]
    ow["type"] = df(PRESET)
    ow["generator"]["settings"] = df(PRESET)
    ow["generator"]["biome_source"] = {"type": "minecraft:multi_noise", "biomes": biome_params}
    write(f"worldgen/world_preset/{PRESET}.json", preset)
    write("tags/worldgen/world_preset/normal.json", {"replace": False, "values": [df(PRESET)]}, ns="minecraft")


def lang(sources):
    base = json.loads((ROOT / "lang" / "en_us.json").read_text(encoding="utf-8"))
    en = json.loads((REF / "lang" / "en_us.json").read_text(encoding="utf-8"))
    for src in sorted((ROOT / "lang").glob("*.json")):
        code = src.stem
        mod = base | json.loads(src.read_text(encoding="utf-8"))
        van_path = REF / "lang" / f"{code}.json"
        van = json.loads(van_path.read_text(encoding="utf-8")) if van_path.exists() else {}
        d = {f"generator.{NS}.{PRESET}": mod["preset"]}
        d.update({f"{NS}.{k}": v for k, v in mod.items() if k != "preset" and not k.startswith("biome.")})
        for name in sources:
            key = f"biome.minecraft.{name}"
            d[f"biome.{NS}.{name}"] = mod.get(f"biome.{name}") or van.get(key) or en[key]
        p = OUT / "assets" / NS / "lang" / f"{code}.json"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(d, ensure_ascii=False, indent=1), encoding="utf-8")

if __name__ == "__main__":
    shutil.rmtree(OUT, ignore_errors=True)
    noises()
    density_functions()
    noise_settings()
    dimension_type()
    remap = layered_vegetation()
    params, sources = biomes(remap, {8: island_lichen()})
    world_preset(params)
    tags = biome_tags(sources)
    lang(sources)
    print(f"biomes {len(sources)}, layered features {len(remap)}, tags {tags}", file=sys.stderr)
