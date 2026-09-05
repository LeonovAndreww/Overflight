package dev.overflight.core.traffic;

import dev.overflight.core.atmo.Isa;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns "put nine fighters in a vee over here" into flights.
 *
 * Shared so that a command typed on a client, a command typed by an operator and
 * relayed to every client, and a call through the public API all build exactly
 * the same aircraft from the same request.
 */
public final class FlightRequests {
    /** How far short of the observer the formation starts, in metres. */
    public static final double APPROACH_M = 12000.0;
    /** How long the aircraft have already been flying, so they arrive trailing. */
    public static final double PREROLL_S = 420.0;
    public static final double DURATION_S = 3600.0;
    /** Spacing between formation members, in wingspans. */
    private static final double SPACING_WINGSPANS = 3.0;

    private FlightRequests() {}

    /**
     * @param flightLevel hundreds of feet, or 0 for the middle of the type's band
     * @param formation   line, vee or echelon; anything else is treated as a vee
     */
    public static List<Flight> build(AircraftType type, double overX, double overZ,
                                     int flightLevel, double headingDeg,
                                     Flight.Condition condition, int count,
                                     String formation, double timeS, long idBase) {
        List<Flight> flights = new ArrayList<Flight>();
        if (type == null || count < 1) {
            return flights;
        }

        int level = flightLevel > 0 ? flightLevel
                : (type.minFlightLevel + type.maxFlightLevel) / 2;
        double altitude = Isa.flightLevelToMetres(level);
        double speed = type.trueAirspeed(Isa.temperature(altitude));
        double heading = StrictMath.toRadians(headingDeg);
        double spacing = type.wingspanM * SPACING_WINGSPANS;
        double rightX = StrictMath.cos(heading);
        double rightZ = StrictMath.sin(heading);
        boolean line = "line".equalsIgnoreCase(formation);
        boolean echelon = "echelon".equalsIgnoreCase(formation);

        for (int i = 0; i < count; i++) {
            double across;
            double behind;
            if (line) {
                across = (i - (count - 1) * 0.5) * spacing;
                behind = 0.0;
            } else if (echelon) {
                across = i * spacing;
                behind = i * spacing;
            } else {
                // A vee: alternate sides, each rank a little further back.
                int side = (i % 2 == 0) ? -1 : 1;
                int rank = (i + 1) / 2;
                across = side * rank * spacing;
                behind = rank * spacing;
            }

            flights.add(ManualTraffic.overhead(idBase + i, type,
                    overX + rightX * across, overZ + rightZ * across,
                    altitude, speed, heading, timeS,
                    APPROACH_M + behind, PREROLL_S, DURATION_S,
                    condition == null ? Flight.Condition.NORMAL : condition));
        }
        return flights;
    }

    /** Seconds until the leader reaches the point it was aimed at. */
    public static long secondsToOverhead(AircraftType type, int flightLevel) {
        int level = flightLevel > 0 ? flightLevel
                : (type.minFlightLevel + type.maxFlightLevel) / 2;
        double altitude = Isa.flightLevelToMetres(level);
        double speed = type.trueAirspeed(Isa.temperature(altitude));
        return StrictMath.round(APPROACH_M / speed);
    }
}
