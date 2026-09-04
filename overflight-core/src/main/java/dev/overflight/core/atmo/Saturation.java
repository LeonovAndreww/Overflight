package dev.overflight.core.atmo;

/**
 * Saturation vapour pressure after Sonntag (1994). Temperature in Kelvin,
 * result in Pascal (the Sonntag fit yields Pa directly). Valid well below the -40 C range where contrails live.
 */
public final class Saturation {

    private Saturation() {}

    /** Saturation vapour pressure over liquid water (supercooled below 273 K). */
    public static double overWater(double tempK) {
        double e = -6096.9385 / tempK
                + 21.2409642
                - 2.711193e-2 * tempK
                + 1.673952e-5 * tempK * tempK
                + 2.433502 * Math.log(tempK);
        return Math.exp(e);
    }

    /** Saturation vapour pressure over ice. */
    public static double overIce(double tempK) {
        double e = -6024.5282 / tempK
                + 29.32707
                + 1.0613868e-2 * tempK
                - 1.3198825e-5 * tempK * tempK
                - 0.49382577 * Math.log(tempK);
        return Math.exp(e);
    }

    /**
     * Convert relative humidity over water into relative humidity over ice.
     * Below freezing RH_ice is always the larger of the two, which is exactly
     * why persistent contrails are possible in air that reads as "dry".
     */
    public static double waterToIce(double rhWater, double tempK) {
        return rhWater * overWater(tempK) / overIce(tempK);
    }
}
