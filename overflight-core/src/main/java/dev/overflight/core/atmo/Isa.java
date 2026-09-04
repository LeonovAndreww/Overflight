package dev.overflight.core.atmo;

/**
 * International Standard Atmosphere: troposphere + lower stratosphere.
 * Altitudes in metres, temperatures in Kelvin, pressures in Pascal.
 *
 * Deliberately Java 8 with no dependencies -- this class must compile on
 * every target from 1.7.10 up.
 */
public final class Isa {
    public static final double SEA_LEVEL_TEMP = 288.15;
    public static final double SEA_LEVEL_PRESSURE = 101325.0;
    public static final double LAPSE_RATE = 0.0065;
    public static final double GRAVITY = 9.80665;
    public static final double GAS_CONSTANT = 287.053;
    public static final double TROPOPAUSE_ALT = 11000.0;

    private static final double TROPOPAUSE_TEMP =
            SEA_LEVEL_TEMP - LAPSE_RATE * TROPOPAUSE_ALT;
    private static final double TROPOPAUSE_PRESSURE =
            SEA_LEVEL_PRESSURE * Math.pow(TROPOPAUSE_TEMP / SEA_LEVEL_TEMP,
                    GRAVITY / (GAS_CONSTANT * LAPSE_RATE));

    private Isa() {}

    public static double temperature(double altitudeM) {
        if (altitudeM < TROPOPAUSE_ALT) {
            return SEA_LEVEL_TEMP - LAPSE_RATE * altitudeM;
        }
        return TROPOPAUSE_TEMP;
    }

    public static double pressure(double altitudeM) {
        if (altitudeM < TROPOPAUSE_ALT) {
            return SEA_LEVEL_PRESSURE * Math.pow(temperature(altitudeM) / SEA_LEVEL_TEMP,
                    GRAVITY / (GAS_CONSTANT * LAPSE_RATE));
        }
        return TROPOPAUSE_PRESSURE * Math.exp(
                -GRAVITY * (altitudeM - TROPOPAUSE_ALT) / (GAS_CONSTANT * TROPOPAUSE_TEMP));
    }

    /** Flight level (hundreds of feet) to metres. FL350 -> 10668 m. */
    public static double flightLevelToMetres(int flightLevel) {
        return flightLevel * 100.0 * 0.3048;
    }
}
