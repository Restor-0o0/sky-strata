package io.github.restor0o0.skystrata;

import static net.minecraft.world.level.levelgen.DensityFunctions.add;
import static net.minecraft.world.level.levelgen.DensityFunctions.constant;
import static net.minecraft.world.level.levelgen.DensityFunctions.max;
import static net.minecraft.world.level.levelgen.DensityFunctions.min;
import static net.minecraft.world.level.levelgen.DensityFunctions.mul;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class StrataDensity implements DensityFunction {
    public static final MapCodec<StrataDensity> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Strata.CODEC.optionalFieldOf("layers", Strata.DEFAULT).forGetter(StrataDensity::strata),
            DensityFunction.CODEC.fieldOf("terrain").forGetter(StrataDensity::terrain),
            DensityFunction.CODEC.fieldOf("land").forGetter(StrataDensity::land),
            StrataNoises.CODEC.forGetter(StrataDensity::noises)
    ).apply(i, StrataDensity::new));
    private static final KeyDispatchDataCodec<StrataDensity> KEY_CODEC = KeyDispatchDataCodec.of(CODEC);
    static final DensityFunction Y = DensityFunctions.yClampedGradient(-4064, 4062, -4064, 4062);
    private static final double SHIFT = 100003.0;

    private final Strata strata;
    private final DensityFunction terrain;
    private final DensityFunction land;
    private final StrataNoises noises;
    private final DensityFunction inner;

    public StrataDensity(Strata strata, DensityFunction terrain, DensityFunction land, StrataNoises noises) {
        this(strata, terrain, land, noises, build(strata, terrain, land, noises));
    }

    private StrataDensity(Strata strata, DensityFunction terrain, DensityFunction land, StrataNoises noises, DensityFunction inner) {
        this.strata = strata;
        this.terrain = terrain;
        this.land = land;
        this.noises = noises;
        this.inner = inner;
    }

    public Strata strata() {
        return strata;
    }

    public DensityFunction terrain() {
        return terrain;
    }

    public DensityFunction land() {
        return land;
    }

    public StrataNoises noises() {
        return noises;
    }

    public StrataDensity withStrata(Strata strata) {
        return new StrataDensity(strata, terrain, land, noises);
    }

    private static DensityFunction build(Strata strata, DensityFunction terrain, DensityFunction land, StrataNoises n) {
        Map<Layer.Kind, Integer> seen = new EnumMap<>(Layer.Kind.class);
        List<DensityFunction> continents = new ArrayList<>(), islands = new ArrayList<>();
        for (Strata.Placed p : strata.placed()) {
            Layer l = p.layer();
            int k = seen.merge(l.kind(), 1, Integer::sum) - 1;
            DensityFunction body = switch (l.kind()) {
                case CONTINENTS -> continent(p, k, land, n);
                case ISLANDS -> island(p, add(noise2d(n.dense(), l, k), mul(constant(0.22), noise2d(n.denseMask(), l, k))),
                        30.0 / 70, 16.0 / 70, 50.0 / 70, n);
                case SHARDS -> island(p, noise2d(n.sparse(), l, k), 40.0 / 77, 10.0 / 77, 38.0 / 77, n);
            };
            (l.kind() == Layer.Kind.CONTINENTS ? continents : islands).add(body);
        }
        DensityFunction out = terrain;
        for (List<DensityFunction> group : List.of(continents, islands)) {
            if (!group.isEmpty()) out = max(out, DensityFunctions.interpolated(group.stream().reduce(DensityFunctions::max).get()));
        }
        return out;
    }

    private static DensityFunction continent(Strata.Placed p, int k, DensityFunction land, StrataNoises n) {
        Layer l = p.layer();
        double f = l.thickness() / 160.0;
        DensityFunction shape = add(noise2d(n.giant(), l, k), add(constant(bias(l)), mul(constant(1.5), add(land, constant(-1.0)))));
        DensityFunction h = add(flat(shape), mul(constant(0.045), DensityFunctions.noise(n.detail(), 1.0, 1.0)));
        DensityFunction top = flat(add(constant(p.top()), mul(constant(25 * f), noise2d(n.giantTop(), l, k))));
        DensityFunction belowTop = add(Y, mul(constant(-1.0), top));
        DensityFunction undercut = mul(constant(1 / (280 * f)), add(belowTop, constant(40 * f)));
        DensityFunction profile = min(min(mul(constant(-1 / 40.0), belowTop), mul(constant(1 / (900 * f)), belowTop)), undercut);
        return gate(p, mul(constant(2.0), add(h, profile)));
    }

    private static DensityFunction island(Strata.Placed p, DensityFunction noise, double center, double up, double down, StrataNoises n) {
        int t = p.layer().thickness();
        DensityFunction h = add(flat(add(noise, constant(bias(p.layer())))), mul(constant(0.12), DensityFunctions.noise(n.detail(), 1.0, 1.0)));
        int c = p.base() + (int) Math.round(center * t);
        int s = 2 * t + 100;
        double topK = 1 / (up * t), bottomK = 1 / (down * t);
        DensityFunction profile = min(DensityFunctions.yClampedGradient(c, c + s, 0.0, -s * topK),
                DensityFunctions.yClampedGradient(c - s, c, -s * bottomK, 0.0));
        return gate(p, mul(constant(2.0), add(h, profile)));
    }

    private static double bias(Layer l) {
        return l.density() * 2.0 - 1.0;
    }

    private static DensityFunction gate(Strata.Placed p, DensityFunction body) {
        return DensityFunctions.rangeChoice(Y, p.gateLo(), p.gateHi(), body, constant(-1.0));
    }

    private static DensityFunction noise2d(Holder<NormalNoise.NoiseParameters> noise, Layer l, int k) {
        return DensityFunctions.shiftedNoise2d(constant(k * SHIFT), constant(-k * SHIFT), 1.0 / l.size(), noise);
    }

    private static DensityFunction flat(DensityFunction f) {
        return DensityFunctions.flatCache(DensityFunctions.cache2d(f));
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
        return new StrataDensity(strata, terrain, land, noises, visitor.apply(inner));
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
