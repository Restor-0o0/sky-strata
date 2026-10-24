package io.github.restor0o0.skystrata;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(SkyStrata.MOD_ID)
public class SkyStrata {
    public static final String MOD_ID = "skystrata";

    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, MOD_ID);

    static {
        DENSITY_FUNCTIONS.register("strata", () -> StrataDensity.CODEC);
        DENSITY_FUNCTIONS.register("strata_biome_depth", () -> StrataBiomeDepth.CODEC);
        DENSITY_FUNCTIONS.register("strata_biome_climate", () -> StrataBiomeClimate.CODEC);
    }

    public SkyStrata(IEventBus bus) {
        DENSITY_FUNCTIONS.register(bus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
