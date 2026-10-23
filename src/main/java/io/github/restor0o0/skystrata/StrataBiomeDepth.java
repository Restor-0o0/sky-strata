package io.github.restor0o0.skystrata;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

public final class StrataBiomeDepth implements DensityFunction {
    public static final MapCodec<StrataBiomeDepth> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Strata.CODEC.optionalFieldOf("layers", Strata.DEFAULT).forGetter(StrataBiomeDepth::strata),
            DensityFunction.CODEC.fieldOf("fallback").forGetter(StrataBiomeDepth::fallback)
    ).apply(i, StrataBiomeDepth::new));
    private static final KeyDispatchDataCodec<StrataBiomeDepth> KEY_CODEC = KeyDispatchDataCodec.of(CODEC);
    private static final double SKY_DEPTH = -2.0;

    private final Strata strata;
    private final DensityFunction fallback;
    private final DensityFunction inner;

    public StrataBiomeDepth(Strata strata, DensityFunction fallback) {
        this(strata, fallback, DensityFunctions.rangeChoice(StrataDensity.Y, -4064, Math.min(strata.skyBiomesFrom(), 4062),
                fallback, DensityFunctions.constant(SKY_DEPTH)));
    }

    private StrataBiomeDepth(Strata strata, DensityFunction fallback, DensityFunction inner) {
        this.strata = strata;
        this.fallback = fallback;
        this.inner = inner;
    }

    public Strata strata() {
        return strata;
    }

    public DensityFunction fallback() {
        return fallback;
    }

    public StrataBiomeDepth withStrata(Strata strata) {
        return new StrataBiomeDepth(strata, fallback);
    }

    @Override
    public double compute(FunctionContext context) {
        return inner.compute(context);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        inner.fillArray(array, provider);
    }

    @Override
    public DensityFunction mapChildren(Visitor visitor) {
        return new StrataBiomeDepth(strata, fallback, visitor.apply(inner));
    }

    @Override
    public double minValue() {
        return inner.minValue();
    }

    @Override
    public double maxValue() {
        return inner.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KEY_CODEC;
    }
}
