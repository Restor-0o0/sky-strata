A world type for Minecraft 1.21.1 built for airships. Giant continents float over a forest floor, or come down to stand on it, with layers of sky islands above. If you play with Create Aeronautics or similar mods, you will need them to get anywhere.

![Layers](https://raw.githubusercontent.com/Restor-0o0/sky-strata/main/docs/layers.png)

## The world

By default the world has three layers:

- **Giant islands** (top surface around y 230–280). Floating continents hundreds of blocks across.
- **Dense sky layer** (y 305–375). Frequent large islands.
- **Shards** (y 385–462). A sparse layer of small floating islands.

The ocean, the coast and the ground between the islands keep the vanilla terrain. Trees and grass grow on every level.

The **Settled** preset brings the giant islands down: some stand on the ground on sheer cliffs, others hover low above it, with the ground they cracked away from between them.

Sky light also falls at an angle and spreads under floating islands, so the ground beneath them is not pitch black. You can turn this off per world.

## Configurable layers

Pick **Sky Strata** as the world type and click **Customize**. Start from a preset (Classic, Settled, Archipelago, Sky Tower, Giants, Shattered) or build your own stack of up to 8 layers. For each layer you set the type (continents, islands or shards), gap, thickness, density and island size, and for continents whether they float, stand on the ground or mix both, and how much landed ones taper toward the ground. The world height grows to fit, up to y 2031.

On a dedicated server set `level-type=skystrata\:sky_strata` in `server.properties`. Layers for new worlds are read from `config/skystrata.json`, either as a preset name or a list of layers. See the [README](https://github.com/Restor-0o0/sky-strata#dedicated-server) for the format.

## Compatibility

- Normal worlds are not affected. The mod does not override any vanilla worldgen file.
- The world type uses its own copies of the vanilla biomes, added to the matching `minecraft:` and `c:` biome tags. Mods that add features, mobs or structures by biome tag work in them.
- Biomes from TerraBlender mods (Biomes O' Plenty and others) do not appear in this world type.

Available for NeoForge 1.21.1, Forge 1.20.1 and NeoForge 26.1. Install on both the server and the clients.

[Source and issues on GitHub](https://github.com/Restor-0o0/sky-strata)
