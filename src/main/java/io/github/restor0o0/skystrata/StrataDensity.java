package io.github.restor0o0.skystrata;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class StrataDensity implements DensityFunction {
    public static final MapCodec<StrataDensity> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Strata.CODEC.optionalFieldOf("layers", Strata.DEFAULT).forGetter(StrataDensity::strata),
            DensityFunction.CODEC.fieldOf("terrain").forGetter(StrataDensity::terrain),
            DensityFunction.CODEC.fieldOf("land").forGetter(StrataDensity::land),
            StrataNoises.CODEC.forGetter(StrataDensity::noises)
    ).apply(i, StrataDensity::new));
    static final DensityFunction Y = DensityFunctions.yClampedGradient(-4064, 4062, -4064, 4062);
    private static final float SHIFT = 100003f;

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
            if (!group.isEmpty()) out = max(out, DensityFunctions.interpolated(group.stream().reduce(DensityFunctions::max).get(), 4, 8));
        }
        return out;
    }

    private static DensityFunction continent(Strata.Placed p, int k, DensityFunction land, StrataNoises n) {
        Layer l = p.layer();
        double f = l.thickness() / 160.0;
        boolean floating = l.landing() == Layer.Landing.FLOATING;
        DensityFunction shape = !floating ? plateaus(l, k, land, n)
                : add(noise2d(n.giant(), l, k), add(constant(bias(l)), mul(constant(1.5), add(land, constant(-1.0)))));
        DensityFunction h = add(flat(shape), mul(constant(0.045), DensityFunctions.noise(n.detail(), 1.0, 1.0)));
        DensityFunction top = flat(add(constant(p.top()), mul(constant(25 * f), noise2d(n.giantTop(), l, k))));
        DensityFunction belowTop = add(Y, mul(constant(-1.0), top));
        DensityFunction cap = mul(constant(-1 / 40.0), belowTop);
        DensityFunction profile;
        if (!floating) {
            float taper = 0.4f * l.taper();
            profile = min(cap, DensityFunctions.yClampedGradient(Strata.GROUND + 10, p.top(), -taper, 0f));
            if (l.landing() == Layer.Landing.MIXED) {
                DensityFunction lift = add(constant(0.3), mul(constant(2.5), noise2d(n.giant(), l, k + 3, 1.0))).clamp(-0.4f, 1.0f);
                DensityFunction bottom = flat(add(constant(Strata.GROUND + 10), mul(constant(40.0), lift)));
                profile = min(profile, mul(constant(1 / 30.0), add(Y, mul(constant(-1.0), bottom))));
            }
        } else {
            DensityFunction undercut = mul(constant(1 / (280 * f)), add(belowTop, constant(40 * f)));
            profile = min(min(cap, mul(constant(1 / (900 * f)), belowTop)), undercut);
        }
        return gate(p, mul(constant(2.0), add(h, profile)));
    }

    private static DensityFunction plateaus(Layer l, int k, DensityFunction land, StrataNoises n) {
        DensityFunction inland = add(mul(constant(3.0), land).clamp(0f, 1f), constant(-1.0));
        DensityFunction cracks = add(noise2d(n.giant(), l, k, 2.0).abs(), constant(0.25 * l.density() - 0.3));
        DensityFunction presence = add(mul(constant(2.0), noise2d(n.giantTop(), l, k + 5, 0.6)), constant(2.4 * l.density() - 0.5));
        return add(min(cracks, presence), mul(constant(1.5), inland));
    }

    private static DensityFunction island(Strata.Placed p, DensityFunction noise, double center, double up, double down, StrataNoises n) {
        int t = p.layer().thickness();
        DensityFunction h = add(flat(add(noise, constant(bias(p.layer())))), mul(constant(0.12), DensityFunctions.noise(n.detail(), 1.0, 1.0)));
        int c = p.base() + (int) Math.round(center * t);
        int s = 2 * t + 100;
        double topK = 1 / (up * t), bottomK = 1 / (down * t);
        DensityFunction profile = min(DensityFunctions.yClampedGradient(c, c + s, 0f, (float) (-s * topK)),
                DensityFunctions.yClampedGradient(c - s, c, (float) (-s * bottomK), 0f));
        return gate(p, mul(constant(2.0), add(h, profile)));
    }

    private static double bias(Layer l) {
        return l.density() * 2.0 - 1.0;
    }

    private static DensityFunction gate(Strata.Placed p, DensityFunction body) {
        return DensityFunctions.rangeChoice(Y, p.gateLo(), p.gateHi(), body, constant(-1.0));
    }

    private static DensityFunction noise2d(Holder<NormalNoise> noise, Layer l, int k) {
        return noise2d(noise, l, k, 1.0);
    }

    private static DensityFunction noise2d(Holder<NormalNoise> noise, Layer l, int k, double scale) {
        return DensityFunctions.shiftedNoise2d(constant(k * SHIFT), constant(-k * SHIFT), scale / l.size(), noise);
    }

    private static DensityFunction add(DensityFunction a, DensityFunction b) {
        return DensityFunctions.add(a, b);
    }

    private static DensityFunction mul(DensityFunction a, DensityFunction b) {
        return DensityFunctions.mul(a, b);
    }

    private static DensityFunction min(DensityFunction a, DensityFunction b) {
        return DensityFunctions.min(a, b);
    }

    private static DensityFunction max(DensityFunction a, DensityFunction b) {
        return DensityFunctions.max(a, b);
    }

    private static DensityFunction constant(double value) {
        return DensityFunctions.constant((float) value);
    }

    private static DensityFunction flat(DensityFunction f) {
        return DensityFunctions.cache(f);
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return inner.compileSampler(context);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction rewritten = rule.rewrite(inner);
        return rewritten == inner ? this : new StrataDensity(strata, terrain, land, noises, rewritten);
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
