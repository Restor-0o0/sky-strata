package io.github.restor0o0.skystrata;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod(SkyStrata.MOD_ID)
public class SkyStrata {
    public static final String MOD_ID = "skystrata";

    private static final DeferredRegister<Codec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, MOD_ID);
    private static final DeferredRegister<PlacementModifierType<?>> PLACEMENTS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, MOD_ID);

    static {
        DENSITY_FUNCTIONS.register("strata", StrataDensity.CODEC::codec);
        DENSITY_FUNCTIONS.register("strata_biome_depth", StrataBiomeDepth.CODEC::codec);
        DENSITY_FUNCTIONS.register("strata_biome_climate", StrataBiomeClimate.CODEC::codec);
    }

    public static final RegistryObject<PlacementModifierType<LayerPlacement>> LAYERS =
            PLACEMENTS.register("layers", () -> () -> LayerPlacement.CODEC);

    public SkyStrata() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        DENSITY_FUNCTIONS.register(bus);
        PLACEMENTS.register(bus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
