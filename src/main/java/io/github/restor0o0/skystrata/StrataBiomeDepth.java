package io.github.restor0o0.skystrata;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;

public final class StrataBiomeDepth implements DensityFunction {
    public static final MapCodec<StrataBiomeDepth> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Strata.CODEC.optionalFieldOf("layers", Strata.DEFAULT).forGetter(StrataBiomeDepth::strata),
            DensityFunction.CODEC.fieldOf("fallback").forGetter(StrataBiomeDepth::fallback)
    ).apply(i, StrataBiomeDepth::new));
    private static final float SKY_DEPTH = -2.0f;

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
    public DensitySampler compileSampler(CompileContext context) {
        return inner.compileSampler(context);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction rewritten = rule.rewrite(inner);
        return rewritten == inner ? this : new StrataBiomeDepth(strata, fallback, rewritten);
    }

    @Override
    public Interval range() {
        return inner.range();
    }

    @Override
    public @Axes int domainAxes() {
        return inner.domainAxes();
    }

    @Override
    public MapCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
