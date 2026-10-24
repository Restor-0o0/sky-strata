package io.github.restor0o0.skystrata.mixin;

import io.github.restor0o0.skystrata.StrataPreset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyLightEngine.class)
abstract class SkyLightEngineMixin extends LightEngine<SkyLightSectionStorage.SkyDataLayerStorageMap, SkyLightSectionStorage> {
    @Unique
    private static final int REACH = 16;
    @Unique
    private static final int ANGLED_LEVEL = 14;
    @Unique
    private static final int VOID_MIN_GAP = 12, VOID_STEP = 6, VOID_MAX_LEVEL = 9, VOID_MIN_Y = 40, VOID_SCAN = 192;
    @Unique
    private static final int[][] RAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    @Unique
    private final BlockPos.MutableBlockPos skystrata$pos = new BlockPos.MutableBlockPos();
    @Unique
    private Boolean skystrata$enabled;

    protected SkyLightEngineMixin(LightChunkGetter chunkSource, SkyLightSectionStorage storage) {
        super(chunkSource, storage);
    }

    @Shadow
    private int getLowestSourceY(int x, int z, int defaultReturnValue) {
        throw new AssertionError();
    }

    @Inject(method = "propagateLightSources", at = @At("TAIL"), require = 0)
    private void skystrata$angledSources(ChunkPos chunk, CallbackInfo ci) {
        if (!skystrata$enabled()) return;
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ();
        int floor = Integer.MAX_VALUE;
        for (int x = x0 - REACH; x < x0 + 16 + REACH; x++) {
            for (int z = z0 - REACH; z < z0 + 16 + REACH; z++) {
                floor = Math.min(floor, getLowestSourceY(x, z, Integer.MAX_VALUE));
            }
        }
        int bottom = SectionPos.sectionToBlockCoord(storage.getBottomSectionY());
        floor = Math.max(floor - REACH / 2 - 1, bottom);
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                int y = getLowestSourceY(x, z, Integer.MIN_VALUE) - 1;
                while (y >= bottom) {
                    if (skystrata$opaque(x, y, z)) {
                        y--;
                        continue;
                    }
                    int end = y;
                    while (end > bottom && !skystrata$opaque(x, end - 1, z)) end--;
                    int open = skystrata$voidLevel(y + 1 - end, end);
                    for (; y >= end; y--) {
                        if (open > 0 || y >= floor) skystrata$light(BlockPos.asLong(x, y, z), open);
                    }
                }
            }
        }
    }

    @Inject(method = "checkNode", at = @At("TAIL"), require = 0)
    private void skystrata$checkAngled(long pos, CallbackInfo ci) {
        if (skystrata$enabled()) skystrata$relight(pos);
    }

    @Inject(method = "propagateDecrease", at = @At("HEAD"), require = 0)
    private void skystrata$keepAngled(long pos, long entry, CallbackInfo ci) {
        if (skystrata$enabled()) skystrata$relight(pos);
    }

    @Unique
    private void skystrata$relight(long pos) {
        int x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
        if (storage.getStoredLevel(pos) >= ANGLED_LEVEL || skystrata$opaque(x, y, z)) return;
        int top = y, end = y;
        while (top - y < VOID_SCAN && !skystrata$opaque(x, top + 1, z)) top++;
        while (y - end < VOID_SCAN && !skystrata$opaque(x, end - 1, z)) end--;
        skystrata$light(pos, skystrata$voidLevel(top + 1 - end, end));
    }

    @Unique
    private static int skystrata$voidLevel(int gap, int floorY) {
        if (floorY < VOID_MIN_Y) return 0;
        return Mth.clamp((gap - VOID_MIN_GAP) / VOID_STEP, 0, VOID_MAX_LEVEL);
    }

    @Unique
    private void skystrata$light(long pos, int open) {
        int x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
        if (!storage.storingLightForSection(SectionPos.blockToSection(pos))) return;
        if (y >= getLowestSourceY(x, z, Integer.MAX_VALUE) || storage.getStoredLevel(pos) >= ANGLED_LEVEL) return;
        BlockState state = getState(skystrata$pos.set(x, y, z));
        int block = state.getLightBlock(chunkSource.getLevel(), skystrata$pos);
        if (block > 1) return;
        int stored = storage.getStoredLevel(pos);
        int level = ANGLED_LEVEL - block > stored && skystrata$seesSky(x, y, z) ? ANGLED_LEVEL - block : open - block;
        if (level > stored) {
            enqueueIncrease(pos, LightEngine.QueueEntry.increaseLightFromEmission(level, isEmptyShape(state)));
        }
    }

    @Unique
    private boolean skystrata$seesSky(int x, int y, int z) {
        for (int[] ray : RAYS) {
            int px = x, py = y, pz = z;
            for (int t = 1; t <= REACH; t++) {
                int rx = x + ray[0] * t, ry = y + (t + 1) / 2, rz = z + ray[1] * t;
                if (ray[0] != 0 && ray[1] != 0 && (skystrata$opaque(rx, py, pz) || skystrata$opaque(px, py, rz))) break;
                if (skystrata$opaque(rx, py, rz)) break;
                int source = getLowestSourceY(rx, rz, Integer.MAX_VALUE);
                if (py >= source) return true;
                if (ry > py) {
                    if (skystrata$opaque(rx, ry, rz)) break;
                    if (ry >= source) return true;
                }
                px = rx;
                py = ry;
                pz = rz;
            }
        }
        return false;
    }

    @Unique
    private boolean skystrata$opaque(int x, int y, int z) {
        return getState(skystrata$pos.set(x, y, z)).getLightBlock(chunkSource.getLevel(), skystrata$pos) > 0;
    }

    @Unique
    private boolean skystrata$enabled() {
        if (skystrata$enabled == null) {
            skystrata$enabled = chunkSource.getLevel() instanceof ServerLevel level
                    && level.dimensionTypeRegistration().unwrapKey().map(StrataPreset::islandLight).orElse(false);
        }
        return skystrata$enabled;
    }
}
