package dev.overflight.core.atmo;

/**
 * What light is falling on a trail, and what colour it is.
 *
 * The part that matters is geometric. A trail is ten kilometres up, so it sees
 * the sun for a while after the sun has set for whoever is standing underneath
 * it: the higher you are, the further round the earth you can see. That is why
 * contrails go on burning orange overhead once the ground has gone blue, and why
 * the highest ones are the last to go out.
 *
 * The colour follows from the same geometry. Light reaching a trail at that hour
 * has come the long way through the atmosphere, and what survives the trip is
 * the red end of it.
 */
public final class Illumination {

    /** Mean radius of the earth, metres. */
    public static final double EARTH_RADIUS_M = 6371000.0;

    /** Colour of low sunlight once the blue has been scattered out of it. */
    private static final double SUNSET_GREEN = 0.48;
    private static final double SUNSET_BLUE = 0.24;

    /** Moonlight, which the eye reads as cooler than daylight. */
    private static final double MOON_RED = 0.82;
    private static final double MOON_GREEN = 0.88;
    private static final double MOON_BLUE = 1.0;

    private Illumination() {}

    /**
     * How far below the observer's horizon the sun can be while still lighting
     * something at this altitude, in degrees.
     *
     * A little over three degrees at airliner cruise, which is a few minutes of
     * real evening and the reason the sky keeps its trails after sunset.
     */
    public static double shadowOffsetDegrees(double altitudeM) {
        double ratio = EARTH_RADIUS_M / (EARTH_RADIUS_M + Math.max(altitudeM, 0.0));
        return Math.toDegrees(Math.acos(Math.min(1.0, ratio)));
    }

    /**
     * The sun's elevation as the trail sees it: its elevation for the observer,
     * raised by however far the trail can see past the horizon.
     */
    public static double effectiveElevationDegrees(double sunElevationDeg, double altitudeM) {
        return sunElevationDeg + shadowOffsetDegrees(altitudeM);
    }

    /**
     * 0 once the trail is in the earth's shadow, 1 in open sun, with the edge
     * softened because the terminator is not a line and the sun is not a point.
     */
    public static double sunlight(double effectiveElevationDeg) {
        return smoothstep(-0.7, 1.6, effectiveElevationDeg);
    }

    /**
     * How far the light has reddened, 0 white to 1 fully sunset-coloured.
     *
     * Rises as the sun drops because its light is crossing more and more
     * atmosphere; by the time the trail is only just still lit, nothing but the
     * red end is getting through.
     */
    public static double warmth(double effectiveElevationDeg) {
        return 1.0 - smoothstep(-0.5, 9.0, effectiveElevationDeg);
    }

    /**
     * The colour of the light on a trail, written into {@code out} as red, green
     * and blue.
     *
     * @param sunlight how much of the light is the sun's rather than the moon's
     */
    public static void tint(double sunlight, double warmth, double[] out) {
        double sunR = 1.0;
        double sunG = 1.0 - (1.0 - SUNSET_GREEN) * warmth;
        double sunB = 1.0 - (1.0 - SUNSET_BLUE) * warmth;

        double blend = clamp01(sunlight);
        out[0] = MOON_RED + (sunR - MOON_RED) * blend;
        out[1] = MOON_GREEN + (sunG - MOON_GREEN) * blend;
        out[2] = MOON_BLUE + (sunB - MOON_BLUE) * blend;
    }

    /**
     * Tilts a sun direction the way a shader pack tilts the sun's path.
     *
     * Minecraft runs the sun straight overhead: it rises due east, crosses the
     * zenith and sets due west. Packs commonly rotate that track so it leans to
     * one side, the way the sun does anywhere but the equator, and a pack's
     * setting of -40 puts the noon sun at fifty degrees rather than ninety.
     *
     * The rotation is about the east-west line, which is the one that keeps
     * sunrise and sunset where they were and only lowers what happens between
     * them.
     *
     * @param sunX,sunY the untilted direction, as Minecraft has it
     * @param out       receives the tilted direction
     */
    public static void rotateSunPath(double sunX, double sunY, double rotationDegrees,
                                     double[] out) {
        double angle = Math.toRadians(rotationDegrees);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        out[0] = sunX;
        out[1] = sunY * cos;
        out[2] = sunY * sin;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double value) {
        return value < 0.0 ? 0.0 : (value > 1.0 ? 1.0 : value);
    }
}
