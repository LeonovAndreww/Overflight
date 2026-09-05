package dev.overflight.core.traffic;

import java.util.ArrayList;
import java.util.List;

/**
 * Flights that were asked for rather than derived.
 *
 * Ambient traffic needs no storage because it follows from a seed, but a flight
 * someone called up by hand has to be kept somewhere. This is that somewhere,
 * and it is what a debug command, an admin command and the public API all put
 * their aircraft into.
 */
public final class ManualTraffic {
    private final List<Flight> flights = new ArrayList<Flight>();

    /** Puts an aircraft into the sky. Returns it so a caller can report the id. */
    public synchronized Flight add(Flight flight) {
        flights.add(flight);
        return flight;
    }

    /** Everything still airborne at the given moment, forgetting whatever has landed. */
    public synchronized List<Flight> collect(double timeS) {
        List<Flight> alive = new ArrayList<Flight>();
        for (int i = flights.size() - 1; i >= 0; i--) {
            Flight flight = flights.get(i);
            if (timeS > flight.startTimeS + flight.durationS) {
                flights.remove(i);
            } else if (timeS >= flight.startTimeS) {
                alive.add(flight);
            }
        }
        return alive;
    }

    public synchronized int size() {
        return flights.size();
    }

    public synchronized int clear() {
        int removed = flights.size();
        flights.clear();
        return removed;
    }

    /**
     * Builds a flight that passes directly over a point.
     *
     * The aircraft is placed short of the observer and given a head start, so it
     * arrives already trailing instead of appearing with nothing behind it.
     *
     * @param headingRad where the aircraft is going: 0 is north, increasing clockwise
     * @param approachM  how far short of the observer it currently is
     * @param prerollS   how long it has already been flying
     */
    public static Flight overhead(long id, AircraftType type, double overX, double overZ,
                                  double altitudeM, double speedMs, double headingRad,
                                  double timeS, double approachM, double prerollS,
                                  double durationS, Flight.Condition condition) {
        double dirX = StrictMath.sin(headingRad);
        double dirZ = -StrictMath.cos(headingRad);

        // Where it should be right now, then wound back along its own track.
        double nowX = overX - dirX * approachM;
        double nowZ = overZ - dirZ * approachM;
        double startX = nowX - dirX * speedMs * prerollS;
        double startZ = nowZ - dirZ * speedMs * prerollS;

        return new Flight(id, type, startX, startZ, headingRad, altitudeM, speedMs,
                timeS - prerollS, durationS, 0, 1, condition);
    }
}
