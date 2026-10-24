package io.github.restor0o0.skystrata;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;

public final class StrataPreset {
    public static final ResourceKey<NoiseGeneratorSettings> SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS, SkyStrata.id("sky_strata"));
    private static final int MIN_NOISE_HEIGHT = 384;
    private static final String VANILLA_LIGHT = "_vanilla_light";

    private StrataPreset() {
    }

    public static boolean islandLight(ResourceKey<DimensionType> key) {
        return key.identifier().getNamespace().equals(SkyStrata.MOD_ID) && !key.identifier().getPath().contains(VANILLA_LIGHT);
    }

    public static boolean islandLight(WorldDimensions dims) {
        LevelStem stem = dims.dimensions().get(LevelStem.OVERWORLD);
        return stem == null || stem.type().unwrapKey().map(StrataPreset::islandLight).orElse(true);
    }

    public static boolean skyBiomes(WorldDimensions dims) {
        LevelStem stem = dims.dimensions().get(LevelStem.OVERWORLD);
        return stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator gen)
                || !(Strata.unwrap(gen.generatorSettings().value().noiseRouter().depth()) instanceof StrataBiomeDepth depth)
                || depth.skyBiomes();
    }

    public static Optional<Holder.Reference<DimensionType>> dimensionType(HolderLookup.Provider access, int height, boolean islandLight) {
        return access.lookupOrThrow(Registries.DIMENSION_TYPE).listElements()
                .filter(h -> h.key().identifier().getNamespace().equals(SkyStrata.MOD_ID) && islandLight(h.key()) == islandLight)
                .filter(h -> h.value().minY() == Strata.MIN_Y && h.value().height() >= height)
                .min(Comparator.comparingInt(h -> h.value().height()));
    }

    public static int maxHeight(HolderLookup.Provider access) {
        return access.lookupOrThrow(Registries.DIMENSION_TYPE).listElements()
                .filter(h -> h.key().identifier().getNamespace().equals(SkyStrata.MOD_ID) && h.value().minY() == Strata.MIN_Y)
                .mapToInt(h -> h.value().height()).max().orElse(0);
    }

    public static WorldDimensions apply(HolderLookup.Provider access, WorldDimensions dims, Strata strata, boolean islandLight, boolean skyBiomes) {
        LevelStem stem = dims.dimensions().get(LevelStem.OVERWORLD);
        if (stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator gen)) return dims;
        Holder<NoiseGeneratorSettings> base = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(SETTINGS);
        NoiseGeneratorSettings s = base.value();
        NoiseRouter r = s.noiseRouter();
        if (!(Strata.unwrap(r.finalDensity()) instanceof StrataDensity density)
                || !(Strata.unwrap(r.depth()) instanceof StrataBiomeDepth depth)) return dims;
        int height = Math.max(MIN_NOISE_HEIGHT, strata.requiredHeight());
        Holder<DimensionType> type = dimensionType(access, height, islandLight).orElseThrow(() -> new IllegalStateException(
                "Sky Strata layers need a world height of " + height + ", the maximum is " + maxHeight(access)));

        Holder<NoiseGeneratorSettings> settings = base;
        if (!strata.equals(density.strata()) || !skyBiomes || height > s.noiseSettings().height()) {
            DensityFunction continents = skyBiomes ? r.continents() : new StrataBiomeClimate(strata, StrataBiomeClimate.Parameter.CONTINENTALNESS, r.continents());
            DensityFunction ridges = skyBiomes ? r.ridges() : new StrataBiomeClimate(strata, StrataBiomeClimate.Parameter.WEIRDNESS, r.ridges());
            NoiseRouter router = new NoiseRouter(r.temperature(), r.vegetation(), continents, r.erosion(),
                    depth.with(strata, skyBiomes), ridges, r.chunkSurfaceLevel(), density.withStrata(strata));
            NoiseSettings noise = new NoiseSettings(s.noiseSettings().minY(), Math.min(height, type.value().height()));
            settings = Holder.direct(new NoiseGeneratorSettings(noise, s.defaultBlock(), s.defaultFluid(), router, s.materialRule(),
                    s.spawnTarget(), s.seaLevel(), s.disableMobGeneration(), s.aquifers(), s.useLegacyRandomSource(), s.debugFunctions()));
        }
        ChunkGenerator generator = new NoiseBasedChunkGenerator(gen.getBiomeSource(), settings);
        Map<ResourceKey<LevelStem>, LevelStem> map = new LinkedHashMap<>(dims.dimensions());
        map.put(LevelStem.OVERWORLD, new LevelStem(type, generator));
        return new WorldDimensions(map);
    }
}
