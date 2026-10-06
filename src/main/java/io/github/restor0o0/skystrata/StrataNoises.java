package io.github.restor0o0.skystrata;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public record StrataNoises(Holder<NormalNoise.NoiseParameters> giant, Holder<NormalNoise.NoiseParameters> giantTop,
                           Holder<NormalNoise.NoiseParameters> dense, Holder<NormalNoise.NoiseParameters> denseMask,
                           Holder<NormalNoise.NoiseParameters> sparse, Holder<NormalNoise.NoiseParameters> detail) {
    public static final MapCodec<StrataNoises> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveElement(key("isl_giant")),
            RegistryOps.retrieveElement(key("isl_giant_top")),
            RegistryOps.retrieveElement(key("isl_dense")),
            RegistryOps.retrieveElement(key("isl_dense_mask")),
            RegistryOps.retrieveElement(key("isl_sparse")),
            RegistryOps.retrieveElement(key("isl_detail"))
    ).apply(i, StrataNoises::new));

    private static ResourceKey<NormalNoise.NoiseParameters> key(String name) {
        return ResourceKey.create(Registries.NOISE, SkyStrata.id(name));
    }
}
