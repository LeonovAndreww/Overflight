package dev.overflight.forge17;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import cpw.mods.fml.common.Loader;
import dev.overflight.core.config.OverflightConfig;

/** Reads and writes config/overflight.json; a file that does not parse is left alone. */
public final class ConfigIo {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigIo() {}

    public static File path() {
        return new File(Loader.instance().getConfigDir(), "overflight.json");
    }

    public static OverflightConfig load() {
        File file = path();
        if (!file.exists()) {
            OverflightConfig fresh = new OverflightConfig().applyPreset().sanitise();
            save(fresh);
            return fresh;
        }
        try (Reader reader = new FileReader(file)) {
            OverflightConfig loaded = GSON.fromJson(reader, OverflightConfig.class);
            if (loaded == null) {
                loaded = new OverflightConfig();
            }
            return loaded.applyPreset().sanitise();
        } catch (IOException | JsonParseException e) {
            Overflight17.LOG.error("Could not read " + file + ", running on defaults and leaving the file alone", e);
            return new OverflightConfig().applyPreset().sanitise();
        }
    }

    public static void save(OverflightConfig config) {
        File file = path();
        try {
            file.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(file)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            Overflight17.LOG.error("Could not write " + file, e);
        }
    }
}
