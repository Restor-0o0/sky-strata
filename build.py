import json
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).parent
REF = ROOT / "vanilla"
VAN = REF / "data" / "minecraft"
OUT = ROOT / "build" / "pack"
DIST = ROOT / "build"

NS = "skystrata"
NAME = "Sky Strata"
VERSION = "1.0.0"
PACK_FORMAT = 48
PRESET = "sky_strata"

MIN_Y = -64
HEIGHT = 528
TERRAIN_TOP_SLIDE = (130, 175)
LAND_LIFT = 0.20
DRY_ABOVE_Y = 10

GIANT = dict(top=255.0, top_var=25.0, top_k=1 / 40, bottom_k=1 / 900, undercut_k=1 / 280, undercut_from=40.0, bias=0.08, wall_noise=0.045, floor=20, ceiling=305)
DENSE = dict(center=335, top_k=1 / 16, bottom_k=1 / 50, bias=-0.1, gate=(305, 375))
SPARSE = dict(center=425, top_k=1 / 10, bottom_k=1 / 38, bias=-0.48, gate=(385, 462))

SKY_BIOMES = {
    "sky_taiga": ("taiga", (-2.0, -0.6), (-2.0, 2.0)),
    "sky_meadow": ("meadow", (-0.6, -0.2), (-2.0, 0.0)),
    "sky_forest": ("forest", (-0.6, -0.2), (0.0, 2.0)),
    "sky_cherry": ("cherry_grove", (-0.2, 0.2), (-2.0, 2.0)),
    "sky_birch": ("birch_forest", (0.2, 0.6), (-2.0, 2.0)),
    "sky_savanna": ("savanna", (0.6, 2.0), (-2.0, 2.0)),
}
SKY_NAMES = {
    "sky_taiga": ("Sky Taiga", "Небесная тайга"), "sky_meadow": ("Sky Meadow", "Небесный луг"),
    "sky_forest": ("Sky Forest", "Небесный лес"), "sky_cherry": ("Cherry Isle", "Вишнёвый остров"),
    "sky_birch": ("Birch Isle", "Берёзовый остров"), "sky_savanna": ("Sky Savanna", "Небесная саванна"),
}
PRESET_NAMES = (NAME, "Небесные пласты")
SKY_DEPTH = -2.0
SKY_BIOME_FROM = 308
OVERHANG_LIGHTS = 256
OVERHANG_LIGHT_Y = (185, 462)
SHADOW_LIGHTS = 256
SHADOW_LIGHT_Y = (40, 450)
SHADOW_COVER = 24
SHADOW_FLOORS = ["grass_block", "podzol", "mycelium", "dirt", "coarse_dirt", "rooted_dirt", "snow_block", "sand", "red_sand", "gravel", "mud"]


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


def interp(x):
    return {"type": "minecraft:interpolated", "argument": x}


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
    lift = mul(LAND_LIFT, land)
    write("worldgen/density_function/offset.json", flat(add("minecraft:overworld/offset", lift)))
    write("worldgen/density_function/depth.json", add(grad(-64, 1.5, 320, -1.5), df("offset")))

    sc = json.dumps(load("worldgen/density_function/overworld/sloped_cheese.json"))
    write("worldgen/density_function/sloped_cheese.json",
          json.loads(sc.replace("minecraft:overworld/depth", df("depth"))))

    write("worldgen/density_function/biome_depth.json", mx(0.0, df("depth")))

    p = GIANT
    shape = add(noise(df("isl_giant")), add(p["bias"], mul(1.5, add(land, -1.0))))
    h = add(flat(shape), mul(p["wall_noise"], noise(df("isl_detail"), 1.0, 1.0)))
    top = flat(add(p["top"], mul(p["top_var"], noise(df("isl_giant_top")))))
    below_top = add("minecraft:y", mul(-1.0, top))
    undercut = mul(p["undercut_k"], add(below_top, p["undercut_from"]))
    profile = mn(mn(mul(-p["top_k"], below_top), mul(p["bottom_k"], below_top)), undercut)
    write("worldgen/density_function/islands_giant.json", yrange(p["floor"], p["ceiling"], mul(2.0, add(h, profile)), -1.0))

    shapes = {"islands_dense": (DENSE, add(noise(df("isl_dense")), add(DENSE["bias"], mul(0.22, noise(df("isl_dense_mask")))))),
              "islands_sparse": (SPARSE, add(noise(df("isl_sparse")), SPARSE["bias"]))}
    for name, (p, shape) in shapes.items():
        c = p["center"]
        h = add(flat(shape), mul(0.12, noise(df("isl_detail"), 1.0, 1.0)))
        profile = mn(grad(c, 0.0, c + 100, -100 * p["top_k"]), grad(c - 100, -100 * p["bottom_k"], c, 0.0))
        lo, hi = p["gate"]
        write(f"worldgen/density_function/{name}.json", yrange(lo, hi, mul(2.0, add(h, profile)), -1.0))


def island_lichen():
    cf = load("worldgen/configured_feature/glow_lichen.json")
    cf["config"].update(search_range=24, chance_of_spreading=1.0)
    cf["config"]["can_be_placed_on"] += ["minecraft:dirt", "minecraft:coarse_dirt"]
    write("worldgen/configured_feature/island_lichen.json", cf)
    write("worldgen/placed_feature/island_lichen.json", {"feature": df("island_lichen"), "placement": [
        {"type": "minecraft:count", "count": 128},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                      "min_inclusive": {"absolute": 60}, "max_inclusive": {"absolute": 380}}},
        {"type": "minecraft:biome"}]})
    return [df("island_lichen")]


def overhang_light():
    air = {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}
    solid = {"type": "minecraft:solid"}
    write("worldgen/configured_feature/overhang_light.json", {"type": "minecraft:simple_block", "config": {"to_place": {
        "type": "minecraft:simple_state_provider",
        "state": {"Name": "minecraft:light", "Properties": {"level": "15", "waterlogged": "false"}}}}})
    roofed = {"type": "minecraft:any_of", "predicates": [dict(solid, offset=[0, k, 0]) for k in (5, 10, 15)]}
    ceiling = ("up", -1, [air, dict(air, offset=[0, -6, 0]), dict(solid, offset=[0, 1, 0])])
    scans = {"ceiling": ceiling,
             "floor": ("down", 1, [air, dict(air, offset=[0, 4, 0]), dict(solid, offset=[0, -1, 0]), roofed])}
    floors = {"type": "minecraft:matching_blocks", "blocks": [f"minecraft:{b}" for b in SHADOW_FLOORS], "offset": [0, -1, 0]}
    scans["shadow"] = ("down", 1, [air, floors], SHADOW_LIGHTS, SHADOW_LIGHT_Y,
                       [{"type": "minecraft:surface_relative_threshold_filter", "heightmap": "WORLD_SURFACE_WG",
                         "max_inclusive": -SHADOW_COVER}])
    for name, (direction, step, preds, *extra) in scans.items():
        count, (lo, hi), filters = extra if extra else (OVERHANG_LIGHTS, OVERHANG_LIGHT_Y, [])
        write(f"worldgen/placed_feature/overhang_light_{name}.json", {"feature": df("overhang_light"), "placement": [
            {"type": "minecraft:count", "count": count},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                          "min_inclusive": {"absolute": lo},
                                                          "max_inclusive": {"absolute": hi}}},
            {"type": "minecraft:environment_scan", "direction_of_search": direction, "max_steps": 32,
             "target_condition": solid, "allowed_search_condition": air},
            {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": step},
            *filters,
            {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": preds}},
            {"type": "minecraft:biome"}]})
    return [df(f"overhang_light_{n}") for n in scans]


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
    s = s.replace('"from_y": 240', f'"from_y": {TERRAIN_TOP_SLIDE[0]}')
    s = s.replace('"to_y": 256', f'"to_y": {TERRAIN_TOP_SLIDE[1]}')
    return json.loads(s)


def noise_settings():
    ns = load("worldgen/noise_settings/overworld.json")
    ns["noise"]["min_y"] = MIN_Y
    ns["noise"]["height"] = HEIGHT
    r = ns["noise_router"]

    terrain = yrange(MIN_Y, TERRAIN_TOP_SLIDE[1] + 8, retarget(r["final_density"]), -1.0)
    giant = interp(df("islands_giant"))
    sky = interp(mx(df("islands_dense"), df("islands_sparse")))
    r["final_density"] = mx(mx(terrain, giant), sky)

    r["fluid_level_floodedness"] = yrange(MIN_Y, DRY_ABOVE_Y, r["fluid_level_floodedness"], -1.0)
    r["initial_density_without_jaggedness"] = retarget(r["initial_density_without_jaggedness"])

    r["depth"] = yrange(MIN_Y, SKY_BIOME_FROM, df("biome_depth"), SKY_DEPTH)
    write(f"worldgen/noise_settings/{PRESET}.json", ns)


def dimension_type():
    dt = load("dimension_type/overworld.json")
    dt.update(min_y=MIN_Y, height=HEIGHT, logical_height=HEIGHT)
    write(f"dimension_type/{PRESET}.json", dt)


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
        b["effects"]["fog_color"] = 0xD8E8FF
        b["effects"]["sky_color"] = 0x8FC4FF
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
    for ns, base in (("minecraft", VAN / "tags" / "worldgen" / "biome"),
                     ("c", REF / "neoforge" / "data" / "c" / "tags" / "worldgen" / "biome")):
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
    for i, code in enumerate(("en_us", "ru_ru")):
        van = json.loads((REF / "lang" / f"{code}.json").read_text(encoding="utf-8"))
        d = {f"generator.{NS}.{PRESET}": PRESET_NAMES[i]}
        for name in sources:
            d[f"biome.{NS}.{name}"] = SKY_NAMES[name][i] if name in SKY_NAMES else van[f"biome.minecraft.{name}"]
        p = OUT / "assets" / NS / "lang" / f"{code}.json"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(d, ensure_ascii=False, indent=1), encoding="utf-8")


DESCRIPTION = "Giant floating continents over a forest floor, plus two layers of sky islands. Adds a separate world type."
REPO = "https://github.com/Restor-0o0/sky-strata"

MODS_TOML = f"""modLoader="lowcodefml"
loaderVersion="[4,)"
license="MIT"
issueTrackerURL="{REPO}/issues"

[[mods]]
modId="{NS}"
version="{VERSION}"
displayName="{NAME}"
authors="Alexey Komarov"
displayURL="{REPO}"
description="{DESCRIPTION}"

[[dependencies.{NS}]]
modId="minecraft"
type="required"
versionRange="[1.21,1.21.2)"
ordering="NONE"
side="BOTH"
"""


def meta():
    (OUT / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": PACK_FORMAT, "description": f"{NAME}: {DESCRIPTION}"}},
                                                indent=1), encoding="utf-8")
    shutil.copy(ROOT / "LICENSE", OUT / "LICENSE")


def zip_dir(src, dst, extra=None):
    with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as z:
        for name, text in (extra or {}).items():
            z.writestr(name, text)
        for f in sorted(src.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(src).as_posix())


def package():
    zip_dir(OUT, DIST / f"{NS}-{VERSION}-datapack.zip")
    zip_dir(OUT, DIST / f"{NS}-{VERSION}.jar", {"META-INF/neoforge.mods.toml": MODS_TOML})


if __name__ == "__main__":
    shutil.rmtree(DIST, ignore_errors=True)
    noises()
    density_functions()
    noise_settings()
    dimension_type()
    remap = layered_vegetation()
    params, sources = biomes(remap, {8: island_lichen(), 10: overhang_light()})
    world_preset(params)
    tags = biome_tags(sources)
    lang(sources)
    meta()
    package()
    print(f"biomes {len(sources)}, layered features {len(remap)}, tags {tags}", file=sys.stderr)
    print("built", sorted(p.name for p in DIST.iterdir()), file=sys.stderr)
