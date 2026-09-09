package dev.overflight.api;

import dev.overflight.core.atmo.Isa;
import dev.overflight.core.atmo.SchmidtAppleman;
import dev.overflight.core.config.OverflightConfig;
import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.AircraftType;
import dev.overflight.core.traffic.Flight;
import dev.overflight.core.traffic.ManualTraffic;
import dev.overflight.fabric.SkyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.Collections;
import java.util.List;

/**
 * The public face of Overflight, for other mods and for whatever a server owner
 * decides an aircraft overhead should mean.
 *
 * The mod itself only ever draws. Nothing here spawns an entity, drops an item
 * or touches the world -- it reports what is in the sky and lets something put
 * an aircraft there. What happens when a burning aircraft passes over a base is
 * for the caller to decide.
 *
 * Every method is safe to call before the client has finished starting; they
 * return empty results rather than throwing.
 */
public final class Overflight {
    /**
     * Version of this API.
     *
     * Raised only when something already here changes meaning or disappears;
     * additions do not move it. Check it if you depend on this from another mod
     * and would rather fail loudly than subtly:
     *
     * <pre>if (Overflight.API_VERSION != 1) { ... }</pre>
     */
    public static final int API_VERSION = 1;

    private static final AircraftCatalog CATALOG = AircraftCatalog.defaults();
    private static final double TICKS_PER_SECOND = 20.0;

    private static SkyRenderer renderer;

    private Overflight() {}

    /** Called by the mod during client startup. Not for callers. */
    public static void install(SkyRenderer skyRenderer) {
        renderer = skyRenderer;
    }

    /** False before startup finishes, or if the sky is switched off in the config. */
    public static boolean available() {
        return renderer != null && renderer.config().enabled;
    }

    /** The live configuration. Changes take effect after {@link #reapplyConfig()}. */
    public static OverflightConfig config() {
        return renderer == null ? null : renderer.config();
    }

    /** Rebuilds everything derived from the config after you have changed it. */
    public static void reapplyConfig() {
        if (renderer != null) {
            renderer.applyConfig(renderer.config());
        }
    }

    /** Every aircraft category that can be flown, with its wingspan, engines and cruise band. */
    public static List<AircraftType> types() {
        return CATALOG.types();
    }

    public static AircraftType type(String id) {
        return CATALOG.byId(id);
    }

    /**
     * Everything airborne within {@code radiusM} of a point right now, ambient
     * traffic and hand-placed flights alike.
     *
     * A {@link Flight} is a function of time rather than a snapshot: ask it for a
     * position at any moment, past or future, and it will answer.
     */
    public static List<Flight> flightsNear(double x, double z, double radiusM) {
        ClientLevel level = level();
        if (renderer == null || level == null) {
            return Collections.emptyList();
        }
        double timeS = timeOf(level);
        List<Flight> flights = renderer.traffic().collect(
                SkyRenderer.seedForLevel(level), timeS, x, z, radiusM,
                renderer.densityPerHour(), renderer.maxAircraft());
        flights.addAll(renderer.manualTraffic().collect(timeS));
        return flights;
    }

    /**
     * Puts an aircraft in the sky on a track that passes over a point.
     *
     * @param typeId       a category id from {@link #types()}
     * @param flightLevel  hundreds of feet, or 0 to take the middle of the type's band
     * @param headingDeg   where it is going: 0 north, 90 east
     * @param condition    normal, or smoking or burning to have it trail soot
     * @return the flight, or null if the type is unknown or the client is not ready
     */
    public static Flight spawn(String typeId, double overX, double overZ, int flightLevel,
                               double headingDeg, Flight.Condition condition) {
        ClientLevel level = level();
        AircraftType type = CATALOG.byId(typeId);
        if (renderer == null || level == null || type == null) {
            return null;
        }

        int chosen = flightLevel > 0 ? flightLevel
                : (type.minFlightLevel + type.maxFlightLevel) / 2;
        double altitude = Isa.flightLevelToMetres(chosen);
        double speed = type.trueAirspeed(Isa.temperature(altitude));
        double timeS = timeOf(level);

        return renderer.manualTraffic().add(ManualTraffic.overhead(
                System.nanoTime(), type, overX, overZ, altitude, speed,
                Math.toRadians(headingDeg), timeS, 12000.0, 420.0, 3600.0,
                condition == null ? Flight.Condition.NORMAL : condition));
    }

    /** Removes one hand-placed flight. Ambient traffic cannot be removed; it is not stored. */
    public static boolean remove(long flightId) {
        return renderer != null && renderer.manualTraffic().remove(flightId);
    }

    /** Removes every hand-placed flight, returning how many went. */
    public static int clearManual() {
        return renderer == null ? 0 : renderer.manualTraffic().clear();
    }

    /**
     * What the air is doing at a point, and what an aircraft would leave there.
     */
    public static Air airAt(double x, double z, int flightLevel) {
        ClientLevel level = level();
        double altitude = Isa.flightLevelToMetres(flightLevel);
        double temperature = Isa.temperature(altitude);
        double pressure = Isa.pressure(altitude);
        double humidity = 0.0;
        if (renderer != null && renderer.humidity() != null && level != null) {
            humidity = renderer.humidity().relativeHumidity(
                    x, z, altitude, timeOf(level), level.getRainLevel(1.0f));
        }
        AircraftType reference = CATALOG.byId("airliner_narrowbody");
        boolean forms = SchmidtAppleman.formsContrail(
                temperature, pressure, humidity, reference.engine);
        double overIce = SchmidtAppleman.persistenceRatio(temperature, humidity);
        return new Air(altitude, temperature, pressure, humidity, overIce, forms);
    }

    /** A reading of the air at one altitude. */
    public static final class Air {
        public final double altitudeM;
        public final double temperatureK;
        public final double pressurePa;
        /** Relative humidity over water, 0 to 1 and occasionally beyond. */
        public final double relativeHumidity;
        /** Over ice. Above 1 a trail feeds on the surrounding air and spreads. */
        public final double iceSaturationRatio;
        /** Whether an ordinary airliner would leave anything here at all. */
        public final boolean contrailForms;

        Air(double altitudeM, double temperatureK, double pressurePa, double relativeHumidity,
            double iceSaturationRatio, boolean contrailForms) {
            this.altitudeM = altitudeM;
            this.temperatureK = temperatureK;
            this.pressurePa = pressurePa;
            this.relativeHumidity = relativeHumidity;
            this.iceSaturationRatio = iceSaturationRatio;
            this.contrailForms = contrailForms;
        }

        public boolean contrailPersists() {
            return contrailForms && iceSaturationRatio > 1.0;
        }
    }

    private static ClientLevel level() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? null : client.level;
    }

    private static double timeOf(ClientLevel level) {
        return level.getGameTime() / TICKS_PER_SECOND;
    }
}
