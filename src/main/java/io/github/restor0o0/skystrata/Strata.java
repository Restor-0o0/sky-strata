package io.github.restor0o0.skystrata;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

public record Strata(List<Layer> layers) {
    public static final int GROUND = 63;
    public static final int MIN_Y = -64;
    public static final int MAX_LAYERS = 8;
    public static final Strata DEFAULT = new Strata(List.of(
            Layer.Kind.CONTINENTS.defaults(), Layer.Kind.ISLANDS.defaults(), Layer.Kind.SHARDS.defaults()));
    public static final Codec<Strata> CODEC = Layer.CODEC.listOf(0, MAX_LAYERS).xmap(Strata::new, Strata::layers);

    public Strata {
        layers = List.copyOf(layers);
    }

    public record Placed(Layer layer, int base, int top, int gateLo, int gateHi) {
    }

    public List<Placed> placed() {
        List<Placed> out = new ArrayList<>();
        int prev = GROUND;
        for (Layer l : layers) {
            int base = prev + l.gap();
            int top = base + l.thickness();
            if (l.kind() == Layer.Kind.CONTINENTS) {
                int lo = l.landing() == Layer.Landing.FLOATING ? Math.max(MIN_Y, base - 75) : GROUND - 40;
                out.add(new Placed(l, base, top, lo, top + 50));
            } else {
                out.add(new Placed(l, base, top, base, top));
            }
            prev = top;
        }
        return out;
    }

    public int maxY() {
        return placed().stream().mapToInt(Placed::gateHi).max().orElse(GROUND + 128);
    }

    public int requiredHeight() {
        return Math.ceilDiv(maxY() + 1 - MIN_Y, 16) * 16;
    }

    public int skyBiomesFrom() {
        return placed().stream().filter(p -> p.layer().kind() != Layer.Kind.CONTINENTS)
                .mapToInt(Placed::base).findFirst().orElse(Integer.MAX_VALUE);
    }

    @Nullable
    public static Strata of(ChunkGenerator generator) {
        if (generator instanceof NoiseBasedChunkGenerator noise
                && unwrap(noise.generatorSettings().value().noiseRouter().finalDensity()) instanceof StrataDensity d) {
            return d.strata();
        }
        return null;
    }

    public static DensityFunction unwrap(DensityFunction f) {
        while (f instanceof DensityFunctions.HolderHolder h) f = h.function().value();
        return f;
    }
}
