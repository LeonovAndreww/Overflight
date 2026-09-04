package dev.overflight.core.atmo;

/**
 * The Schmidt-Appleman criterion: does an exhaust plume cross water saturation
 * as it mixes with ambient air, and if it does, will the resulting ice crystals
 * survive or sublimate.
 *
 * Formation and persistence are two independent questions. Formation depends on
 * ambient temperature and pressure; persistence depends only on whether the air
 * is supersaturated with respect to ice.
 */
public final class SchmidtAppleman {

    /** Specific heat of air at constant pressure, J/(kg K). */
    private static final double CP = 1004.0;
    /** Ratio of molar masses, water vapour to dry air. */
    private static final double EPSILON = 0.622;

    private SchmidtAppleman() {}

    /**
     * Slope of the plume mixing line, Pa/K.
     *
     * @param pressurePa       ambient pressure
     * @param waterEmissionIdx kg of water vapour per kg of fuel burned (~1.25 for kerosene)
     * @param heatOfCombustion J/kg (~43e6 for jet A)
     * @param propulsionEff    fraction of fuel energy that becomes thrust rather than heat
     */
    public static double mixingLineSlope(double pressurePa, double waterEmissionIdx,
                                         double heatOfCombustion, double propulsionEff) {
        return waterEmissionIdx * CP * pressurePa
                / (EPSILON * heatOfCombustion * (1.0 - propulsionEff));
    }

    /**
     * Threshold temperature for a plume mixing into air that is already
     * saturated over water. Empirical fit, Kelvin.
     */
    public static double thresholdTempAtWaterSaturation(double slope) {
        double lg = StrictMath.log(StrictMath.max(slope - 0.053, 1e-9));
        return 226.69 + 9.43 * lg + 0.720 * lg * lg;
    }

    /**
     * Critical temperature for the actual ambient humidity: a contrail forms
     * when ambient temperature is at or below this value. Solved by bisection
     * because the mixing line meets an exponential saturation curve.
     *
     * @param rhWater relative humidity over water, 0..1+
     */
    public static double criticalTemperature(double slope, double rhWater) {
        double tlm = thresholdTempAtWaterSaturation(slope);
        if (rhWater >= 1.0) {
            return tlm;
        }
        double ewTlm = Saturation.overWater(tlm);
        double lo = tlm - 50.0;
        double hi = tlm;
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            double f = rhWater * Saturation.overWater(mid) - ewTlm + slope * (tlm - mid);
            if (f > 0.0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    /** True when the exhaust condenses at all. */
    public static boolean formsContrail(double ambientTempK, double ambientPressurePa,
                                        double rhWater, EngineProfile engine) {
        double slope = mixingLineSlope(ambientPressurePa, engine.waterEmissionIndex,
                engine.heatOfCombustion, engine.propulsionEfficiency);
        return ambientTempK <= criticalTemperature(slope, rhWater);
    }

    /**
     * Ice supersaturation ratio. Above 1.0 the trail feeds on ambient moisture
     * and spreads; below it the crystals sublimate within seconds.
     */
    public static double persistenceRatio(double ambientTempK, double rhWater) {
        return Saturation.waterToIce(rhWater, ambientTempK);
    }

    /** Per-aircraft engine characteristics feeding the criterion. */
    public static final class EngineProfile {
        public final double waterEmissionIndex;
        public final double heatOfCombustion;
        public final double propulsionEfficiency;

        public EngineProfile(double waterEmissionIndex, double heatOfCombustion,
                             double propulsionEfficiency) {
            this.waterEmissionIndex = waterEmissionIndex;
            this.heatOfCombustion = heatOfCombustion;
            this.propulsionEfficiency = propulsionEfficiency;
        }

        /** Modern high-bypass turbofan: efficient, so it trails more readily. */
        public static EngineProfile modernTurbofan() {
            return new EngineProfile(1.25, 43.0e6, 0.37);
        }

        /** Older low-bypass turbojet: hotter exhaust, needs colder air. */
        public static EngineProfile lowBypassTurbojet() {
            return new EngineProfile(1.25, 43.0e6, 0.22);
        }

        public static EngineProfile turboprop() {
            return new EngineProfile(1.25, 43.0e6, 0.42);
        }
    }
}
