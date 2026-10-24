package io.github.restor0o0.skystrata.mixin;

import io.github.restor0o0.skystrata.ServerConfig;
import io.github.restor0o0.skystrata.Strata;
import io.github.restor0o0.skystrata.StrataPreset;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DedicatedServerProperties.class)
abstract class DedicatedServerPropertiesMixin {
    @Inject(method = "createDimensions", at = @At("RETURN"), cancellable = true)
    private void skystrata$applyLayers(HolderLookup.Provider access, CallbackInfoReturnable<WorldDimensions> cir) {
        WorldDimensions dims = cir.getReturnValue();
        if (Strata.of(dims.overworld()) != null) {
            ServerConfig.Settings settings = ServerConfig.load();
            cir.setReturnValue(StrataPreset.apply(access, dims, settings.strata(), settings.islandLight()));
        }
    }
}
