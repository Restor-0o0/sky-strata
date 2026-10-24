package io.github.restor0o0.skystrata;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

public final class ServerConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<Either<LayerPresets, Strata>> CODEC = Codec.either(
            LayerPresets.CODEC.fieldOf("preset").codec(), Strata.CODEC.fieldOf("layers").codec());

    private ServerConfig() {
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(SkyStrata.MOD_ID + ".json");
    }

    public record Settings(Strata strata, boolean islandLight) {
    }

    public static Settings load() {
        Path path = path();
        try {
            if (Files.notExists(path)) {
                JsonElement json = CODEC.encodeStart(JsonOps.INSTANCE, Either.left(LayerPresets.ALL.getFirst())).getOrThrow();
                Files.createDirectories(path.getParent());
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
                LOGGER.info("Created {}", path);
            }
            JsonElement json = JsonParser.parseString(Files.readString(path));
            Strata strata = CODEC.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(error -> new IllegalStateException("Invalid " + path + ": " + error))
                    .map(LayerPresets::strata, s -> s);
            boolean islandLight = !(json instanceof JsonObject o && o.has("island_light")) || o.get("island_light").getAsBoolean();
            LOGGER.info("Generating Sky Strata layers from {}: {}, island light {}", path, strata.layers(), islandLight);
            return new Settings(strata, islandLight);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }
}
