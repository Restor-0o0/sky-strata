package io.github.restor0o0.skystrata.client;

import io.github.restor0o0.skystrata.SkyStrata;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;

@EventBusSubscriber(modid = SkyStrata.MOD_ID, value = Dist.CLIENT)
public final class SkyStrataClient {
    private SkyStrataClient() {
    }

    @SubscribeEvent
    public static void presetEditors(RegisterPresetEditorsEvent event) {
        event.register(ResourceKey.create(Registries.WORLD_PRESET, SkyStrata.id("sky_strata")), StrataScreen::new);
    }
}
