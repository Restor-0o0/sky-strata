# Changelog

## 1.2.2

- Islands and continents have caves: the vanilla cheese, spaghetti and noodle caves with entrances on the surface. Shards stay solid.
- Copper, gold, redstone, lapis and diamond ores generate inside every layer, placed relative to the layer instead of the absolute height. Diamonds, redstone and gold sit in the lower part of a layer, copper in the upper part.
- In existing worlds the caves and ores appear only in newly generated chunks.

## 1.2.1

- New **Biomes** button on the Customize screen. *Sky* keeps the sky biomes on the islands and shards above the continents. *Local* gives them the biome of the place below them, like the continents and the ground. On a dedicated server set `"sky_biomes": false` in `config/skystrata.json`.
- Removed the glow lichen that 1.2.0 scattered on the islands. It showed up as yellow spots on the surface.
- The island light button is now labeled **Light**.

## 1.2.0

- Continents layers have a new **Landing** setting: *Floating* (as before), *Mixed* (some giant islands stand on the ground on sheer cliffs, others hover low above it) and *Settled* (all of them stand on the ground). Landed islands have wide gaps of normal terrain between them; the layer's density sets how narrow the gaps are. A new **Taper** slider sets how much landed islands narrow toward the ground.
- New preset **Settled**: the Classic layers with mixed landing.
- Sky light in Sky Strata worlds also falls at an angle, so the ground under the edges of floating islands is lit about 16 blocks in. Further in, the space under an island is lit dimly, and brighter the higher the island floats. Caves stay dark. Other worlds are not affected. It can be turned off per world with the new Island light button, or with `"island_light": false` in the server config.
- Existing worlds and configs keep floating continents and generate as before.

## 1.1.0

First public release.

- New world type **Sky Strata** for Minecraft 1.21.1 (NeoForge): giant floating continents over a forest floor, a dense layer of sky islands and a sparse layer of shards above it.
- Layer editor on the world creation screen (**Customize**): add, remove and reorder up to 8 layers and set each layer's type, gap, thickness, density and island size. The world height grows to fit the layers, up to y 2031.
- Presets: Classic, Archipelago, Sky Tower, Giants, Shattered.
- Dedicated servers read the layers for new worlds from `config/skystrata.json`.
- Copies of the vanilla overworld biomes, added to the matching `minecraft:` and `c:` biome tags, plus six sky biomes for the island layers.
- Translations: English, Russian, Ukrainian, German, French, Spanish, Brazilian Portuguese, Polish, Italian, Turkish, Simplified and Traditional Chinese, Japanese, Korean.
