import json
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


def load(rel):
    return json.loads((VAN / rel).read_text(encoding="utf-8"))


def write(rel, obj, ns=NS):
    p = OUT / "data" / ns / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, indent=1, ensure_ascii=False), encoding="utf-8")


def df(name):
    return f"{NS}:{name}"


def add(a, b):
    return {"type": "minecraft:add", "argument1": a, "argument2": b}


def mul(a, b):
    return {"type": "minecraft:mul", "argument1": a, "argument2": b}


def mn(a, b):
    return {"type": "minecraft:min", "argument1": a, "argument2": b}


def mx(a, b):
    return {"type": "minecraft:max", "argument1": a, "argument2": b}


def clamp(x, lo, hi):
    return {"type": "minecraft:clamp", "input": x, "min": lo, "max": hi}


def noise(n, xz=1.0, y=0.0):
    return {"type": "minecraft:noise", "noise": n, "xz_scale": xz, "y_scale": y}


def flat(x):
    return {"type": "minecraft:flat_cache", "argument": {"type": "minecraft:cache_2d", "argument": x}}


def grad(fy, fv, ty, tv):
    return {"type": "minecraft:y_clamped_gradient", "from_y": fy, "from_value": fv, "to_y": ty, "to_value": tv}


def yrange(lo, hi, inside, outside):
    return {"type": "minecraft:range_choice", "input": "minecraft:y",
            "min_inclusive": lo, "max_exclusive": hi,
            "when_in_range": inside, "when_out_of_range": outside}


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
        write(f"worldgen/noise/{name}.json", {"firstOctave": oct_, "amplitudes": amps})


def density_functions():
    land = clamp(mul(4.0, add("minecraft:overworld/continents", 0.15)), 0.0, 1.0)
    write("worldgen/density_function/land.json", land)
    write("worldgen/density_function/offset.json", flat(add("minecraft:overworld/offset", mul(LAND_LIFT, df("land")))))
    write("worldgen/density_function/depth.json", add(grad(-64, 1.5, 320, -1.5), df("offset")))

    sc = json.dumps(load("worldgen/density_function/overworld/sloped_cheese.json"))
    write("worldgen/density_function/sloped_cheese.json",
          json.loads(sc.replace("minecraft:overworld/depth", df("depth"))))

    write("worldgen/density_function/biome_depth.json", mx(0.0, df("depth")))


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
        return dict(pf, feature=self.ref("configured_feature", pf["feature"], self.configured))

    def configured(self, cf):
        cf = json.loads(json.dumps(cf))
        c = cf.get("config", {})
        if cf["type"] == "minecraft:tree":
            c["decorators"] = [d for d in c.get("decorators", []) if d["type"] not in UNSAFE_DECORATORS]
        for key in ("default", "feature_true", "feature_false", "feature"):
            if key in c:
                c[key] = self.ref("placed_feature", c[key], self.placed)
        if "features" in c:
            c["features"] = [dict(e, feature=self.ref("placed_feature", e["feature"], self.placed)) if isinstance(e, dict) and "chance" in e
                             else self.ref("placed_feature", e, self.placed) for e in c["features"]]
        return cf


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
    s = s.replace('"from_y": 240', f'"from_y": {TERRAIN_TOP_SLIDE[0]}')
    s = s.replace('"to_y": 256', f'"to_y": {TERRAIN_TOP_SLIDE[1]}')
    return json.loads(s)


def noise_settings():
    ns = load("worldgen/noise_settings/overworld.json")
    ns["noise"]["min_y"] = MIN_Y
    ns["noise"]["height"] = HEIGHTS[0]
    r = ns["noise_router"]

    write("worldgen/density_function/terrain.json", yrange(MIN_Y, TERRAIN_TOP_SLIDE[1] + 8, retarget(r["final_density"]), -1.0))
    r["final_density"] = {"type": f"{NS}:strata", "terrain": df("terrain"), "land": df("land")}

    r["fluid_level_floodedness"] = yrange(MIN_Y, DRY_ABOVE_Y, r["fluid_level_floodedness"], -1.0)
    r["preliminary_surface_level"] = retarget(r["preliminary_surface_level"])

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
    params, sources = biomes(remap, {})
    world_preset(params)
    tags = biome_tags(sources)
    lang(sources)
    print(f"biomes {len(sources)}, layered features {len(remap)}, tags {tags}", file=sys.stderr)
