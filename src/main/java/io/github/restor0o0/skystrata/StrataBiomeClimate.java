package io.github.restor0o0.skystrata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;

public final class StrataBiomeClimate implements DensityFunction {
    public static final MapCodec<StrataBiomeClimate> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Strata.CODEC.optionalFieldOf("layers", Strata.DEFAULT).forGetter(StrataBiomeClimate::strata),
            Parameter.CODEC.fieldOf("parameter").forGetter(StrataBiomeClimate::parameter),
            DensityFunction.CODEC.fieldOf("input").forGetter(StrataBiomeClimate::input)
    ).apply(i, StrataBiomeClimate::new));
    private static final float INLAND = 0.1f, OUT_OF_VALLEY = 0.06f;

    private final Strata strata;
    private final Parameter parameter;
    private final DensityFunction input;
    private final DensityFunction inner;

    public StrataBiomeClimate(Strata strata, Parameter parameter, DensityFunction input) {
        this(strata, parameter, input, DensityFunctions.rangeChoice(StrataDensity.Y, -4064, Math.min(strata.skyBiomesFrom(), 4062),
                input, parameter.onIslands(input)));
    }

    private StrataBiomeClimate(Strata strata, Parameter parameter, DensityFunction input, DensityFunction inner) {
        this.strata = strata;
        this.parameter = parameter;
        this.input = input;
        this.inner = inner;
    }

    public Strata strata() {
        return strata;
    }

    public Parameter parameter() {
        return parameter;
    }

    public DensityFunction input() {
        return input;
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return inner.compileSampler(context);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction rewritten = rule.rewrite(inner);
        return rewritten == inner ? this : new StrataBiomeClimate(strata, parameter, input, rewritten);
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

    public enum Parameter implements StringRepresentable {
        CONTINENTALNESS("continentalness"),
        WEIRDNESS("weirdness");

        public static final Codec<Parameter> CODEC = StringRepresentable.fromEnum(Parameter::values);
        private final String name;

        Parameter(String name) {
            this.name = name;
        }

        // Islands over the sea or a river would otherwise get ocean, beach or river biomes.
        private DensityFunction onIslands(DensityFunction f) {
            if (this == CONTINENTALNESS) return DensityFunctions.max(f, DensityFunctions.constant(INLAND));
            return DensityFunctions.rangeChoice(f, -OUT_OF_VALLEY, 0f, DensityFunctions.constant(-OUT_OF_VALLEY),
                    DensityFunctions.rangeChoice(f, 0f, OUT_OF_VALLEY, DensityFunctions.constant(OUT_OF_VALLEY), f));
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
