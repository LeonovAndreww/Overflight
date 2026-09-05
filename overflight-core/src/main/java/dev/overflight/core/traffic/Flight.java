package dev.overflight.core.traffic;

/**
 * One aircraft on one straight cruise leg.
 *
 * Immutable and derived rather than ticked: position is a function of time, so
 * the same flight can be evaluated for any moment, on any client, in any order,
 * and two machines that never exchanged a packet agree on where it is.
 *
 * Horizontal coordinates are world blocks, which are metres. Altitude is metres
 * above sea level and routinely exceeds the build limit by a factor of thirty --
 * these are never world positions, only directions and angular sizes to draw at.
 */
public final class Flight {
    public final long id;
    public final AircraftType type;
    /** Where the leg begins, at startTime. */
    public final double startX;
    public final double startZ;
    /** Compass heading in radians: 0 is north, increasing clockwise. */
    public final double heading;
    public final double altitudeM;
    public final double groundSpeedMs;
    public final double startTimeS;
    public final double durationS;
    /** Index within a formation, 0 for a lone aircraft. */
    public final int formationIndex;
    public final int formationSize;
    /** What the aircraft is doing to its exhaust. Ordinary traffic is always {@link Condition#NORMAL}. */
    public final Condition condition;

    /**
     * Whether the aircraft is flying normally or trailing something other than
     * ice. Ambient traffic never uses anything but NORMAL; the rest exists so a
     * command, or another mod through the API, can put up an aircraft that is
     * visibly in trouble and let the server decide what that means on the ground.
     */
    public enum Condition {
        NORMAL,
        SMOKING,
        BURNING,
        DESCENDING
    }

    public Flight(long id, AircraftType type, double startX, double startZ, double heading,
                  double altitudeM, double groundSpeedMs, double startTimeS, double durationS,
                  int formationIndex, int formationSize) {
        this(id, type, startX, startZ, heading, altitudeM, groundSpeedMs, startTimeS,
                durationS, formationIndex, formationSize, Condition.NORMAL);
    }

    public Flight(long id, AircraftType type, double startX, double startZ, double heading,
                  double altitudeM, double groundSpeedMs, double startTimeS, double durationS,
                  int formationIndex, int formationSize, Condition condition) {
        this.id = id;
        this.type = type;
        this.startX = startX;
        this.startZ = startZ;
        this.heading = heading;
        this.altitudeM = altitudeM;
        this.groundSpeedMs = groundSpeedMs;
        this.startTimeS = startTimeS;
        this.durationS = durationS;
        this.formationIndex = formationIndex;
        this.formationSize = formationSize;
        this.condition = condition;
    }

    public boolean airborneAt(double timeS) {
        return timeS >= startTimeS && timeS <= startTimeS + durationS;
    }

    /** Distance flown along the leg by the given moment, in metres. */
    public double distanceAt(double timeS) {
        return groundSpeedMs * (timeS - startTimeS);
    }

    public double xAt(double timeS) {
        return startX + StrictMath.sin(heading) * distanceAt(timeS);
    }

    /** North is negative Z in Minecraft, hence the sign. */
    public double zAt(double timeS) {
        return startZ - StrictMath.cos(heading) * distanceAt(timeS);
    }

    /**
     * Horizontal distance from a point, in metres. Used to drop flights that are
     * too far away to be worth drawing.
     */
    public double horizontalDistanceFrom(double timeS, double x, double z) {
        double dx = xAt(timeS) - x;
        double dz = zAt(timeS) - z;
        return StrictMath.sqrt(dx * dx + dz * dz);
    }

    /**
     * How close this flight came to a point between two moments, in metres.
     *
     * Asking where the aircraft is right now is the wrong question when deciding
     * whether to draw it: a trail reaches hundreds of kilometres behind, so an
     * aircraft long over the horizon can still have left something directly
     * overhead. This asks where it has been instead.
     */
    public double closestApproach(double fromTimeS, double toTimeS, double x, double z) {
        double from = StrictMath.max(fromTimeS, startTimeS);
        double to = StrictMath.min(toTimeS, startTimeS + durationS);
        if (to < from) {
            return Double.MAX_VALUE;
        }

        double dirX = StrictMath.sin(heading) * groundSpeedMs;
        double dirZ = -StrictMath.cos(heading) * groundSpeedMs;
        double offsetX = startX - x;
        double offsetZ = startZ - z;

        double speedSquared = dirX * dirX + dirZ * dirZ;
        double best = from - startTimeS;
        if (speedSquared > 0.0) {
            // Where the track passes nearest, clamped to the window we care about.
            double nearest = -(offsetX * dirX + offsetZ * dirZ) / speedSquared;
            double lo = from - startTimeS;
            double hi = to - startTimeS;
            best = nearest < lo ? lo : (nearest > hi ? hi : nearest);
        }

        double dx = offsetX + dirX * best;
        double dz = offsetZ + dirZ * best;
        return StrictMath.sqrt(dx * dx + dz * dz);
    }
}
