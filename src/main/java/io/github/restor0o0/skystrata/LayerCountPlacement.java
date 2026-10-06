package io.github.restor0o0.skystrata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

public class LayerCountPlacement extends PlacementModifier {
    public static final MapCodec<LayerCountPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.floatRange(0, 16).fieldOf("per_block").forGetter(p -> p.perBlock),
            Codec.BOOL.optionalFieldOf("from_ground", false).forGetter(p -> p.fromGround)
    ).apply(i, LayerCountPlacement::new));

    private final float perBlock;
    private final boolean fromGround;

    public LayerCountPlacement(float perBlock, boolean fromGround) {
        this.perBlock = perBlock;
        this.fromGround = fromGround;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        Strata strata = Strata.of(context.generator());
        if (strata == null) return Stream.empty();
        int lo = context.getMinGenY(), hi = lo + context.getGenDepth() - 1;
        List<int[]> bands = new ArrayList<>();
        int total = 0;
        for (int[] b : strata.lightBands(fromGround)) {
            int from = Math.max(lo, b[0]), to = Math.min(hi, b[1]);
            if (to > from) {
                bands.add(new int[]{from, to});
                total += to - from;
            }
        }
        if (total == 0) return Stream.empty();
        double expected = perBlock * total;
        int count = (int) expected + (random.nextDouble() < expected % 1 ? 1 : 0);
        int sum = total;
        return IntStream.range(0, count).mapToObj(n -> {
            int r = random.nextInt(sum);
            for (int[] b : bands) {
                if (r < b[1] - b[0]) return pos.atY(b[0] + r);
                r -= b[1] - b[0];
            }
            return pos;
        });
    }

    @Override
    public PlacementModifierType<?> type() {
        return SkyStrata.LAYER_COUNT.get();
    }
}
