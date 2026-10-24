package io.github.restor0o0.skystrata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

public final class StrataBiomeClimate implements DensityFunction {
    public static final MapCodec<StrataBiomeClimate> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Strata.CODEC.optionalFieldOf("layers", Strata.DEFAULT).forGetter(StrataBiomeClimate::strata),
            Parameter.CODEC.fieldOf("parameter").forGetter(StrataBiomeClimate::parameter),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(StrataBiomeClimate::input)
    ).apply(i, StrataBiomeClimate::new));
    private static final KeyDispatchDataCodec<StrataBiomeClimate> KEY_CODEC = KeyDispatchDataCodec.of(CODEC);
    private static final double INLAND = 0.1, OUT_OF_VALLEY = 0.06;

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
    public double compute(FunctionContext context) {
        return inner.compute(context);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        inner.fillArray(array, provider);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new StrataBiomeClimate(strata, parameter, input, inner.mapAll(visitor)));
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
            return DensityFunctions.rangeChoice(f, -OUT_OF_VALLEY, 0.0, DensityFunctions.constant(-OUT_OF_VALLEY),
                    DensityFunctions.rangeChoice(f, 0.0, OUT_OF_VALLEY, DensityFunctions.constant(OUT_OF_VALLEY), f));
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
