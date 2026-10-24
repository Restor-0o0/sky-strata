package io.github.restor0o0.skystrata;

import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
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
        return key.location().getNamespace().equals(SkyStrata.MOD_ID) && !key.location().getPath().contains(VANILLA_LIGHT);
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

    public static Optional<Holder.Reference<DimensionType>> dimensionType(RegistryAccess access, int height, boolean islandLight) {
        return access.registryOrThrow(Registries.DIMENSION_TYPE).holders()
                .filter(h -> h.key().location().getNamespace().equals(SkyStrata.MOD_ID) && islandLight(h.key()) == islandLight)
                .filter(h -> h.value().minY() == Strata.MIN_Y && h.value().height() >= height)
                .min(Comparator.comparingInt(h -> h.value().height()));
    }

    public static int maxHeight(RegistryAccess access) {
        return access.registryOrThrow(Registries.DIMENSION_TYPE).holders()
                .filter(h -> h.key().location().getNamespace().equals(SkyStrata.MOD_ID) && h.value().minY() == Strata.MIN_Y)
                .mapToInt(h -> h.value().height()).max().orElse(0);
    }

    public static WorldDimensions apply(RegistryAccess access, WorldDimensions dims, Strata strata, boolean islandLight, boolean skyBiomes) {
        LevelStem stem = dims.dimensions().get(LevelStem.OVERWORLD);
        if (stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator gen)) return dims;
        Holder<NoiseGeneratorSettings> base = access.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(SETTINGS);
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
            NoiseRouter router = new NoiseRouter(r.barrierNoise(), r.fluidLevelFloodednessNoise(), r.fluidLevelSpreadNoise(), r.lavaNoise(),
                    r.temperature(), r.vegetation(), continents, r.erosion(), depth.with(strata, skyBiomes), ridges,
                    r.initialDensityWithoutJaggedness(), density.withStrata(strata), r.veinToggle(), r.veinRidged(), r.veinGap());
            NoiseSettings ns = s.noiseSettings();
            NoiseSettings noise = new NoiseSettings(ns.minY(), Math.min(height, type.value().height()), ns.noiseSizeHorizontal(), ns.noiseSizeVertical());
            settings = Holder.direct(new NoiseGeneratorSettings(noise, s.defaultBlock(), s.defaultFluid(), router, s.surfaceRule(),
                    s.spawnTarget(), s.seaLevel(), s.disableMobGeneration(), s.aquifersEnabled(), s.oreVeinsEnabled(), s.useLegacyRandomSource()));
        }
        ChunkGenerator generator = new NoiseBasedChunkGenerator(gen.getBiomeSource(), settings);
        return new WorldDimensions(WorldDimensions.withOverworld(dims.dimensions(), type, generator));
    }
}
