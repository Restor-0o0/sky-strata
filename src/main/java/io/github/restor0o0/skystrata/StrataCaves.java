package io.github.restor0o0.skystrata;

import static net.minecraft.world.level.levelgen.DensityFunctions.add;
import static net.minecraft.world.level.levelgen.DensityFunctions.constant;
import static net.minecraft.world.level.levelgen.DensityFunctions.max;
import static net.minecraft.world.level.levelgen.DensityFunctions.min;
import static net.minecraft.world.level.levelgen.DensityFunctions.mul;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public record StrataCaves(Holder<DensityFunction> entrances, Holder<DensityFunction> pillars,
                          Holder<NormalNoise.NoiseParameters> layer, Holder<NormalNoise.NoiseParameters> cheese,
                          Holder<NormalNoise.NoiseParameters> noodle, Holder<NormalNoise.NoiseParameters> noodleThickness,
                          Holder<NormalNoise.NoiseParameters> noodleRidgeA, Holder<NormalNoise.NoiseParameters> noodleRidgeB) {
    public static final MapCodec<StrataCaves> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveElement(function("overworld/caves/entrances")),
            RegistryOps.retrieveElement(function("overworld/caves/pillars")),
            RegistryOps.retrieveElement(noise("cave_layer")),
            RegistryOps.retrieveElement(noise("cave_cheese")),
            RegistryOps.retrieveElement(noise("noodle")),
            RegistryOps.retrieveElement(noise("noodle_thickness")),
            RegistryOps.retrieveElement(noise("noodle_ridge_a")),
            RegistryOps.retrieveElement(noise("noodle_ridge_b"))
    ).apply(i, StrataCaves::new));
    public static final double DEPTH_SCALE = 4.0;
    private static final double ROOMS = 0.27, LAYERING = 1.0;
    private static final double SURFACE = 1.5625;

    // Vanilla overworld caves with the island body in place of sloped_cheese.
    public DensityFunction carve(DensityFunction body) {
        DensityFunction entrances = new DensityFunctions.HolderHolder(this.entrances);
        DensityFunction pillars = new DensityFunctions.HolderHolder(this.pillars);
        DensityFunction rooms = add(mul(constant(LAYERING), DensityFunctions.noise(layer, 1.0, 8.0).square()),
                add(add(constant(ROOMS), DensityFunctions.noise(cheese, 1.0, 2.0 / 3)).clamp(-1.0, 1.0),
                        add(constant(1.5), mul(constant(-0.64), body)).clamp(0.0, 0.5)));
        DensityFunction deep = max(min(rooms, entrances),
                DensityFunctions.rangeChoice(pillars, -1000000.0, 0.03, constant(-1000000.0), pillars));
        return min(DensityFunctions.rangeChoice(body, -1000000.0, SURFACE, min(body, mul(constant(5.0), entrances)), deep), noodles());
    }

    private DensityFunction noodles() {
        double ridge = 8.0 / 3;
        DensityFunction tunnel = add(add(constant(-0.075), mul(constant(-0.025), DensityFunctions.noise(noodleThickness, 1.0, 1.0))),
                mul(constant(1.5), max(DensityFunctions.noise(noodleRidgeA, ridge, ridge).abs(), DensityFunctions.noise(noodleRidgeB, ridge, ridge).abs())));
        DensityFunction n = DensityFunctions.noise(noodle, 1.0, 1.0);
        return DensityFunctions.rangeChoice(n, -1000000.0, 0.0, constant(64.0), tunnel);
    }

    private static ResourceKey<DensityFunction> function(String name) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.withDefaultNamespace(name));
    }

    private static ResourceKey<NormalNoise.NoiseParameters> noise(String name) {
        return ResourceKey.create(Registries.NOISE, Identifier.withDefaultNamespace(name));
    }
}
