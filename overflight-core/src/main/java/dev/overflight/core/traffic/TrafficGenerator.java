package dev.overflight.core.traffic;

import dev.overflight.core.atmo.Isa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Produces the aircraft overhead at a given moment.
 *
 * Nothing is spawned or remembered. The map is tiled into cells and time into
 * slots; each pair yields a seed, the seed yields the departures, and asking the
 * same question twice always gives the same sky. That is what lets a server hand
 * a client one seed instead of a stream of positions, and what lets the mod run
 * with no server at all.
 */
public final class TrafficGenerator {
    /** Cell edge in metres. Large enough that a whole leg starts within a neighbour. */
    private static final double CELL_M = 512000.0;
    /** Departures are drawn per cell per slot. */
    private static final double SLOT_S = 600.0;
    /**
     * How long one cruise leg lasts. Must exceed the longest trail lifetime, or
     * a trail would vanish the moment its aircraft finished its leg, minutes
     * before the ice in it would really have gone.
     */
    private static final double LEG_S = 3600.0;
    /** Cells to search either side of the observer's own. */
    private static final int CELL_REACH = 2;
    /** Vertical separation between usable cruise levels, in flight levels. */
    private static final int LEVEL_STEP = 10;
    /** Lateral spacing inside a formation, in wingspans. */
    private static final double FORMATION_SPACING = 3.0;

    private final AircraftCatalog catalog;

    public TrafficGenerator(AircraftCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Every aircraft airborne within {@code radiusM} of the given point.
     *
     * @param worldSeed      shared between all clients; the whole sky follows from it
     * @param timeS          seconds on a clock all clients agree on
     * @param densityPerHour flights entering a 1000 x 1000 km area each hour
     * @param maxAircraft    keeps the nearest this many and drops the rest
     */
    public List<Flight> collect(long worldSeed, double timeS, double centreX, double centreZ,
                                double radiusM, double densityPerHour, int maxAircraft) {
        return collect(worldSeed, timeS, centreX, centreZ, radiusM, densityPerHour,
                maxAircraft, LEG_S);
    }

    /**
     * @param trailWindowS how far back a trail can still be hanging in the sky,
     *                     which is what decides whether a flight is worth keeping
     */
    public List<Flight> collect(long worldSeed, double timeS, double centreX, double centreZ,
                                double radiusM, double densityPerHour, int maxAircraft,
                                double trailWindowS) {
        List<Flight> found = new ArrayList<Flight>();

        long centreCellX = (long) StrictMath.floor(centreX / CELL_M);
        long centreCellZ = (long) StrictMath.floor(centreZ / CELL_M);
        long firstSlot = (long) StrictMath.floor((timeS - LEG_S) / SLOT_S);
        long lastSlot = (long) StrictMath.floor(timeS / SLOT_S);

        // Flights entering a 1000 x 1000 km area per hour, converted to a mean
        // count for one cell over one slot.
        double mean = densityPerHour / (1.0e12 * 3600.0) * CELL_M * CELL_M * SLOT_S;

        for (long cellX = centreCellX - CELL_REACH; cellX <= centreCellX + CELL_REACH; cellX++) {
            for (long cellZ = centreCellZ - CELL_REACH; cellZ <= centreCellZ + CELL_REACH; cellZ++) {
                for (long slot = firstSlot; slot <= lastSlot; slot++) {
                    generateCell(found, worldSeed, cellX, cellZ, slot, mean, timeS,
                            centreX, centreZ, radiusM, trailWindowS);
                }
            }
        }

        if (found.size() > maxAircraft) {
            final double cx = centreX;
            final double cz = centreZ;
            // Ranked over the flight's whole leg rather than over the window
            // ending now. The window slides every frame, so its ranking did too,
            // and a flight that slipped one place past the cap lost its entire
            // trail between one frame and the next -- gone at a stroke rather
            // than fading. Over the whole leg the number does not move at all
            // while the observer stands still, so membership only changes as
            // legs begin and end, which is where trails begin and end anyway.
            Collections.sort(found, new Comparator<Flight>() {
                @Override
                public int compare(Flight a, Flight b) {
                    return Double.compare(
                            a.closestApproach(a.startTimeS, a.startTimeS + a.durationS, cx, cz),
                            b.closestApproach(b.startTimeS, b.startTimeS + b.durationS, cx, cz));
                }
            });
            return new ArrayList<Flight>(found.subList(0, maxAircraft));
        }
        return found;
    }

    private void generateCell(List<Flight> out, long worldSeed, long cellX, long cellZ,
                              long slot, double mean, double timeS,
                              double centreX, double centreZ, double radiusM,
                              double trailWindowS) {
        Rng rng = new Rng(Rng.seedOf(worldSeed, cellX, cellZ, slot));
        int departures = rng.poisson(mean);

        for (int i = 0; i < departures; i++) {
            AircraftType type = catalog.pick(rng);
            double startX = (cellX + rng.nextDouble()) * CELL_M;
            double startZ = (cellZ + rng.nextDouble()) * CELL_M;
            double heading = rng.nextDouble() * 2.0 * StrictMath.PI;
            double startTime = (slot + rng.nextDouble()) * SLOT_S;

            int level = cruiseLevel(rng, type, heading);
            double altitude = Isa.flightLevelToMetres(level);
            double speed = type.trueAirspeed(Isa.temperature(altitude));

            int formation = type.minFormation
                    + (type.maxFormation > type.minFormation
                        ? rng.nextInt(type.maxFormation - type.minFormation + 1) : 0);

            for (int member = 0; member < formation; member++) {
                // Members sit off the leader's wing, so a flight of fighters
                // draws several parallel trails rather than one.
                double offset = member * type.wingspanM * FORMATION_SPACING;
                double sideX = StrictMath.cos(heading) * offset;
                double sideZ = StrictMath.sin(heading) * offset;

                Flight flight = new Flight(
                        Rng.seedOf(worldSeed, cellX, cellZ, slot, i, member),
                        type, startX + sideX, startZ + sideZ, heading, altitude, speed,
                        startTime, LEG_S, member, formation);

                // Kept if its track came close at any point still carrying a
                // trail, not merely if the aircraft is close right now.
                if (flight.startTimeS <= timeS
                        && flight.closestApproach(timeS - trailWindowS, timeS,
                                centreX, centreZ) <= radiusM) {
                    out.add(flight);
                }
            }
        }
    }

    /**
     * Picks a cruise level obeying the semicircular rule: aircraft heading east
     * fly odd levels, aircraft heading west fly even ones, which is what keeps
     * opposing traffic a thousand feet apart in reality and what makes a sky
     * full of crossing trails look right.
     */
    private static int cruiseLevel(Rng rng, AircraftType type, double heading) {
        double degrees = StrictMath.toDegrees(heading);
        boolean eastbound = degrees < 180.0;

        int lowStep = type.minFlightLevel / LEVEL_STEP;
        int highStep = type.maxFlightLevel / LEVEL_STEP;
        int steps = Math.max(1, highStep - lowStep);
        int chosen = lowStep + rng.nextInt(steps + 1);

        // Odd multiples of ten flight levels eastbound, even ones westbound.
        if ((chosen % 2 == 0) == eastbound) {
            chosen += (chosen < highStep) ? 1 : -1;
        }
        return chosen * LEVEL_STEP;
    }
}
