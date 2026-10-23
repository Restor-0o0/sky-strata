package io.github.restor0o0.skystrata.client;

import io.github.restor0o0.skystrata.SkyStrata;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterPresetEditorsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SkyStrata.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SkyStrataClient {
    private SkyStrataClient() {
    }

    @SubscribeEvent
    public static void presetEditors(RegisterPresetEditorsEvent event) {
        event.register(ResourceKey.create(Registries.WORLD_PRESET, SkyStrata.id("sky_strata")), StrataScreen::new);
    }
}
