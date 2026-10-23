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
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;

public final class StrataPreset {
    public static final ResourceKey<NoiseGeneratorSettings> SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS, SkyStrata.id("sky_strata"));
    private static final int MIN_NOISE_HEIGHT = 384;

    private StrataPreset() {
    }

    public static Optional<Holder.Reference<DimensionType>> dimensionType(HolderLookup.Provider access, int height) {
        return access.lookupOrThrow(Registries.DIMENSION_TYPE).listElements()
                .filter(h -> h.key().identifier().getNamespace().equals(SkyStrata.MOD_ID))
                .filter(h -> h.value().minY() == Strata.MIN_Y && h.value().height() >= height)
                .min(Comparator.comparingInt(h -> h.value().height()));
    }

    public static int maxHeight(HolderLookup.Provider access) {
        return access.lookupOrThrow(Registries.DIMENSION_TYPE).listElements()
                .filter(h -> h.key().identifier().getNamespace().equals(SkyStrata.MOD_ID) && h.value().minY() == Strata.MIN_Y)
                .mapToInt(h -> h.value().height()).max().orElse(0);
    }

    public static WorldDimensions apply(HolderLookup.Provider access, WorldDimensions dims, Strata strata) {
        LevelStem stem = dims.dimensions().get(LevelStem.OVERWORLD);
        if (stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator gen)) return dims;
        Holder<NoiseGeneratorSettings> base = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(SETTINGS);
        NoiseGeneratorSettings s = base.value();
        NoiseRouter r = s.noiseRouter();
        if (!(Strata.unwrap(r.finalDensity()) instanceof StrataDensity density)
                || !(Strata.unwrap(r.depth()) instanceof StrataBiomeDepth depth)) return dims;
        int height = Math.max(MIN_NOISE_HEIGHT, strata.requiredHeight());
        Holder<DimensionType> type = dimensionType(access, height).orElseThrow(() -> new IllegalStateException(
                "Sky Strata layers need a world height of " + height + ", the maximum is " + maxHeight(access)));

        Holder<NoiseGeneratorSettings> settings = base;
        if (!strata.equals(density.strata()) || height > s.noiseSettings().height()) {
            NoiseRouter router = new NoiseRouter(r.barrierNoise(), r.fluidLevelFloodednessNoise(), r.fluidLevelSpreadNoise(), r.lavaNoise(),
                    r.temperature(), r.vegetation(), r.continents(), r.erosion(), depth.withStrata(strata), r.ridges(),
                    r.preliminarySurfaceLevel(), density.withStrata(strata), r.veinToggle(), r.veinRidged(), r.veinGap());
            NoiseSettings ns = s.noiseSettings();
            NoiseSettings noise = new NoiseSettings(ns.minY(), Math.min(height, type.value().height()), ns.noiseSizeHorizontal(), ns.noiseSizeVertical());
            settings = Holder.direct(new NoiseGeneratorSettings(noise, s.defaultBlock(), s.defaultFluid(), router, s.surfaceRule(),
                    s.spawnTarget(), s.seaLevel(), s.disableMobGeneration(), s.aquifersEnabled(), s.oreVeinsEnabled(), s.useLegacyRandomSource()));
        }
        ChunkGenerator generator = new NoiseBasedChunkGenerator(gen.getBiomeSource(), settings);
        Map<ResourceKey<LevelStem>, LevelStem> map = new LinkedHashMap<>(dims.dimensions());
        map.put(LevelStem.OVERWORLD, new LevelStem(type, generator));
        return new WorldDimensions(map);
    }
}
