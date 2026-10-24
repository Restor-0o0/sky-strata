package io.github.restor0o0.skystrata;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(SkyStrata.MOD_ID)
public class SkyStrata {
    public static final String MOD_ID = "skystrata";

    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, MOD_ID);
    private static final DeferredRegister<MapCodec<? extends PlacementModifier>> PLACEMENTS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, MOD_ID);

    static {
        DENSITY_FUNCTIONS.register("strata", () -> StrataDensity.CODEC);
        DENSITY_FUNCTIONS.register("strata_biome_depth", () -> StrataBiomeDepth.CODEC);
        DENSITY_FUNCTIONS.register("strata_biome_climate", () -> StrataBiomeClimate.CODEC);
        PLACEMENTS.register("layers", () -> LayerPlacement.CODEC);
    }

    public SkyStrata(IEventBus bus) {
        DENSITY_FUNCTIONS.register(bus);
        PLACEMENTS.register(bus);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
