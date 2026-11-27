package dev.overflight.forge17;

import dev.overflight.core.config.OverflightConfig;

/** The options reachable from chat and from the settings screen: name, type, read and write. */
public final class ConfigOptions {

    private ConfigOptions() {}

    /** Options reachable from chat: name -> type. Look options leave the look preset, traffic ones the traffic preset. */
    public static final java.util.Map<String, Class<?>> TYPES = new java.util.LinkedHashMap<String, Class<?>>();
    static {
        TYPES.put("enabled", Boolean.class);
        TYPES.put("density", Double.class);
        TYPES.put("maxAircraft", Integer.class);
        TYPES.put("opacity", Double.class);
        TYPES.put("maxSkyCover", Double.class);
        TYPES.put("persistence", Double.class);
        TYPES.put("trailDetail", Integer.class);
        TYPES.put("detailedTrails", Boolean.class);
        TYPES.put("drawInSky", Boolean.class);
        TYPES.put("visibleRangeKm", Double.class);
    }

    public static Object read(OverflightConfig c, String key) {
        switch (key) {
            case "enabled": return c.enabled;
            case "density": return c.traffic.densityPerHour;
            case "maxAircraft": return c.traffic.maxAircraft;
            case "opacity": return c.trails.opacity;
            case "maxSkyCover": return c.trails.maxSkyCover;
            case "persistence": return c.trails.persistenceMultiplier;
            case "trailDetail": return c.graphics.trailDetail;
            case "detailedTrails": return c.graphics.detailedTrails;
            case "drawInSky": return c.graphics.drawInSky;
            case "visibleRangeKm": return c.graphics.visibleRangeM / 1000.0;
            default: return null;
        }
    }

    public static void write(OverflightConfig c, String key, Object v) {
        switch (key) {
            case "enabled": c.enabled = (Boolean) v; break;
            case "density": c.traffic.densityPerHour = (Double) v; c.traffic.preset = "custom"; break;
            case "maxAircraft": c.traffic.maxAircraft = (Integer) v; c.traffic.preset = "custom"; break;
            case "opacity": c.trails.opacity = (Double) v; c.preset = "custom"; break;
            case "maxSkyCover": c.trails.maxSkyCover = (Double) v; c.preset = "custom"; break;
            case "persistence": c.trails.persistenceMultiplier = (Double) v; c.preset = "custom"; break;
            case "trailDetail": c.graphics.trailDetail = (Integer) v; c.preset = "custom"; break;
            case "detailedTrails": c.graphics.detailedTrails = (Boolean) v; break;
            case "drawInSky": c.graphics.drawInSky = (Boolean) v; break;
            case "visibleRangeKm": c.graphics.visibleRangeM = (Double) v * 1000.0; break;
            default: break;
        }
    }
}
