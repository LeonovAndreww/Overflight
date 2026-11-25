package dev.overflight.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.overflight.core.config.OverflightConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes config/overflight.json.
 *
 * Gson comes with Minecraft, so the core stays free of dependencies and still
 * gets a config file. A file that will not parse is kept rather than
 * overwritten: losing someone's hand-tuned settings to a stray comma is worse
 * than running on defaults for one session and saying so in the log.
 */
public final class ConfigIo {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverflightClient.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigIo() {}

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("overflight.json");
    }

    public static OverflightConfig load() {
        Path file = path();
        if (!Files.exists(file)) {
            OverflightConfig fresh = new OverflightConfig().applyPreset().sanitise();
            save(fresh);
            return fresh;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            OverflightConfig loaded = GSON.fromJson(reader, OverflightConfig.class);
            if (loaded == null) {
                loaded = new OverflightConfig();
            }
            return loaded.applyPreset().sanitise();
        } catch (IOException | JsonParseException e) {
            LOGGER.error("Could not read {}, running on defaults and leaving the file alone",
                    file, e);
            return new OverflightConfig().applyPreset().sanitise();
        }
    }

    public static void save(OverflightConfig config) {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Could not write {}", file, e);
        }
    }
}
