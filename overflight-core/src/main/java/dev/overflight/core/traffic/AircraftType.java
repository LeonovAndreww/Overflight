package dev.overflight.core.traffic;

import dev.overflight.core.atmo.SchmidtAppleman;

/**
 * One category of aircraft. Everything the simulation needs to place a machine
 * in the sky and decide what it leaves behind it.
 */
public final class AircraftType {
    public final String id;
    /** Relative likelihood against the other types. Normalised by the catalog. */
    public final double weight;
    /** Cruise band in flight levels, hundreds of feet. */
    public final int minFlightLevel;
    public final int maxFlightLevel;
    public final int engineCount;
    public final SchmidtAppleman.EngineProfile engine;
    public final double wingspanM;
    public final double cruiseMach;
    /** Aircraft of this type travel together in groups of this size. */
    public final int minFormation;
    public final int maxFormation;

    public AircraftType(String id, double weight, int minFlightLevel, int maxFlightLevel,
                        int engineCount, SchmidtAppleman.EngineProfile engine,
                        double wingspanM, double cruiseMach,
                        int minFormation, int maxFormation) {
        this.id = id;
        this.weight = weight;
        this.minFlightLevel = minFlightLevel;
        this.maxFlightLevel = maxFlightLevel;
        this.engineCount = engineCount;
        this.engine = engine;
        this.wingspanM = wingspanM;
        this.cruiseMach = cruiseMach;
        this.minFormation = minFormation;
        this.maxFormation = maxFormation;
    }

    public AircraftType(String id, double weight, int minFlightLevel, int maxFlightLevel,
                        int engineCount, SchmidtAppleman.EngineProfile engine,
                        double wingspanM, double cruiseMach) {
        this(id, weight, minFlightLevel, maxFlightLevel, engineCount, engine,
                wingspanM, cruiseMach, 1, 1);
    }

    /**
     * True airspeed at a given altitude. The speed of sound depends only on
     * temperature, so the same Mach number is a different ground speed at
     * different levels -- which is why aircraft higher up visibly outrun the
     * ones below them.
     */
    public double trueAirspeed(double ambientTempK) {
        double speedOfSound = StrictMath.sqrt(1.4 * 287.053 * ambientTempK);
        return cruiseMach * speedOfSound;
    }
}
