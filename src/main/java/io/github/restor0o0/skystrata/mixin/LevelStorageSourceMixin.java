package io.github.restor0o0.skystrata.mixin;

import net.minecraft.nbt.NbtAccounter;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelStorageSource.class)
abstract class LevelStorageSourceMixin {
    // Strata noise settings are stored inline in world_gen_settings.dat and exceed the default 2 MiB read quota.
    @ModifyArg(method = "readExistingSavedData", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/nbt/NbtIo;readCompressed(Ljava/nio/file/Path;Lnet/minecraft/nbt/NbtAccounter;)Lnet/minecraft/nbt/CompoundTag;"))
    private static NbtAccounter skystrata$raiseQuota(NbtAccounter quota) {
        return NbtAccounter.uncompressedQuota();
    }
}
