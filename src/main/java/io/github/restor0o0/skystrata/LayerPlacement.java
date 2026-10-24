package io.github.restor0o0.skystrata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

public class LayerPlacement implements PlacementModifier {
    public static final MapCodec<LayerPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.floatRange(0, 64).fieldOf("count").forGetter(p -> p.count),
            Codec.floatRange(0, 1).fieldOf("from").forGetter(p -> p.from),
            Codec.floatRange(0, 1).fieldOf("to").forGetter(p -> p.to)
    ).apply(i, LayerPlacement::new));
    private static final double PER_THICKNESS = 100.0;

    private final float count;
    private final float from;
    private final float to;

    public LayerPlacement(float count, float from, float to) {
        this.count = count;
        this.from = from;
        this.to = Math.max(from, to);
    }

    @Override
    public void modify(PlacementContext context, RandomSource random, BlockPos pos, Consumer<BlockPos> out) {
        Strata strata = Strata.of(context.generator());
        if (strata == null) return;
        int lo = context.getMinGenY(), hi = lo + context.getGenDepth() - 1;
        for (Strata.Placed p : strata.placed()) {
            int t = p.top() - p.base();
            int from = Math.max(lo, p.base() + Math.round(this.from * t)), to = Math.min(hi, p.base() + Math.round(this.to * t));
            if (to <= from) continue;
            double expected = count * t / PER_THICKNESS;
            int n = (int) expected + (random.nextDouble() < expected % 1 ? 1 : 0);
            for (int k = 0; k < n; k++) {
                out.accept(new BlockPos(pos.getX() + random.nextInt(16), from + random.nextInt(to - from + 1), pos.getZ() + random.nextInt(16)));
            }
        }
    }

    @Override
    public MapCodec<LayerPlacement> codec() {
        return CODEC;
    }
}
