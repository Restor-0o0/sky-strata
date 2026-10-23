package io.github.restor0o0.skystrata;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;
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

    public static Strata load() {
        Path path = path();
        try {
            if (Files.notExists(path)) {
                JsonElement json = CODEC.encodeStart(JsonOps.INSTANCE, Either.left(LayerPresets.ALL.get(0))).result().orElseThrow();
                Files.createDirectories(path.getParent());
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
                LOGGER.info("Created {}", path);
            }
            JsonElement json = JsonParser.parseString(Files.readString(path));
            Strata strata = CODEC.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(false, error -> {
                        throw new IllegalStateException("Invalid " + path + ": " + error);
                    })
                    .map(LayerPresets::strata, s -> s);
            LOGGER.info("Generating Sky Strata layers from {}: {}", path, strata.layers());
            return strata;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }
}
