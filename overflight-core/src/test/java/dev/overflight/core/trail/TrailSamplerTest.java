package dev.overflight.core.trail;

import dev.overflight.core.atmo.Isa;
import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.AircraftType;
import dev.overflight.core.traffic.Flight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a trail is shaped along its length.
 *
 * Several of these guard bugs that actually shipped. Varying the spreading rate
 * on a wavelength shorter than the gap between samples aliased into a row of
 * lumps; hanging a pattern on a piece of trail's age rather than on when it was
 * emitted made that pattern crawl along the trail at flying speed. Both looked
 * plausible in the code and were only obvious on screen, which is exactly the
 * kind of thing worth pinning down here.
 */
class TrailSamplerTest {

    private static final AircraftCatalog CATALOG = AircraftCatalog.defaults();
    private final TrailSampler sampler = new TrailSampler();

    private static Flight widebodyAtCruise() {
        AircraftType type = CATALOG.byId("airliner_widebody");
        double altitude = Isa.flightLevelToMetres(350);
        double speed = type.trueAirspeed(Isa.temperature(altitude));
        return new Flight(1L, type, 0.0, 0.0, 0.0, altitude, speed, 0.0, 3600.0, 0, 1);
    }

    @Test
    @DisplayName("no trail at all when the air cannot make one")
    void nothingWhenItCannotForm() {
        Trail trail = sampler.sample(widebodyAtCruise(), 1200.0, false, false, new TrailSettings());
        assertTrue(trail.isEmpty());
    }

    @Test
    @DisplayName("a burning aircraft smokes even where no contrail could form")
    void smokeIgnoresTheAtmosphere() {
        AircraftType type = CATALOG.byId("airliner_widebody");
        double altitude = Isa.flightLevelToMetres(350);
        double speed = type.trueAirspeed(Isa.temperature(altitude));
        Flight burning = new Flight(2L, type, 0.0, 0.0, 0.0, altitude, speed, 0.0, 3600.0,
                0, 1, Flight.Condition.BURNING);

        Trail trail = sampler.sample(burning, 1200.0, false, false, new TrailSettings());
        assertFalse(trail.isEmpty());
        assertEquals(1.0, trail.sootFraction, 1.0e-9);
    }

    @Test
    @DisplayName("a trail that cannot persist is a stub, not a streak")
    void shortTrailsAreShort() {
        Flight flight = widebodyAtCruise();
        TrailSettings settings = new TrailSettings();

        Trail brief = sampler.sample(flight, 1200.0, true, false, settings);
        Trail lasting = sampler.sample(flight, 1200.0, true, true, settings);

        double briefAge = brief.points.get(brief.points.size() - 1).age;
        double lastingAge = lasting.points.get(lasting.points.size() - 1).age;

        assertTrue(briefAge < 30.0, "a sublimating trail should be seconds long, was " + briefAge);
        assertTrue(lastingAge > briefAge * 10.0,
                "a persistent trail should outlast it by far: " + lastingAge + " vs " + briefAge);
    }

    @Test
    @DisplayName("the trail starts behind the aircraft, not at the nozzle")
    void thereIsAGapBehindTheEngines() {
        Trail trail = sampler.sample(widebodyAtCruise(), 1200.0, true, true, new TrailSettings());
        assertTrue(trail.points.get(0).age > 0.0,
                "the plume has to cool before it freezes, so the youngest sample is not at zero");
    }

    @Test
    @DisplayName("neighbouring samples never jump in width")
    void spreadingDoesNotAlias() {
        Trail trail = sampler.sample(widebodyAtCruise(), 2400.0, true, true, new TrailSettings());

        double worst = 1.0;
        for (int i = 1; i < trail.points.size(); i++) {
            TrailPoint previous = trail.points.get(i - 1);
            TrailPoint current = trail.points.get(i);
            double ratio = Math.max(current.halfWidth / previous.halfWidth,
                    previous.halfWidth / current.halfWidth);
            worst = Math.max(worst, ratio);
        }

        // Whatever varies along a trail has to vary slowly enough that the
        // samples resolve it. Anything much above this is a row of beads.
        assertTrue(worst < 1.5,
                "adjacent samples differ in width by " + worst + "x, which reads as lumps");
    }

    @Test
    @DisplayName("the trail widens and fades from front to back")
    void agesInTheRightDirection() {
        Trail trail = sampler.sample(widebodyAtCruise(), 2400.0, true, true, new TrailSettings());
        TrailPoint youngest = trail.points.get(0);
        TrailPoint oldest = trail.points.get(trail.points.size() - 1);

        assertTrue(oldest.halfWidth > youngest.halfWidth * 5.0,
                "an old trail should be far wider than a fresh one");
        assertTrue(oldest.opacity < youngest.opacity,
                "and fainter");
        assertTrue(oldest.age > youngest.age);
    }

    @Test
    @DisplayName("samples are spent in proportion to how much trail there is")
    void sampleBudgetFollowsLength() {
        Flight flight = widebodyAtCruise();
        TrailSettings settings = new TrailSettings();

        int stub = sampler.sample(flight, 1200.0, true, false, settings).points.size();
        int streak = sampler.sample(flight, 1200.0, true, true, settings).points.size();

        assertTrue(stub < streak / 4,
                "a fifteen-second stub should not cost what a half-hour trail does: "
                        + stub + " vs " + streak);
        assertTrue(streak <= settings.maxPoints);
    }

    @Test
    @DisplayName("the same question twice gives the same trail")
    void deterministic() {
        Flight flight = widebodyAtCruise();
        TrailSettings settings = new TrailSettings();

        Trail first = sampler.sample(flight, 1500.0, true, true, settings);
        Trail second = sampler.sample(flight, 1500.0, true, true, settings);

        assertEquals(first.points.size(), second.points.size());
        for (int i = 0; i < first.points.size(); i++) {
            assertEquals(first.points.get(i).x, second.points.get(i).x, 0.0);
            assertEquals(first.points.get(i).halfWidth, second.points.get(i).halfWidth, 0.0);
            assertEquals(first.points.get(i).opacity, second.points.get(i).opacity, 0.0);
        }
    }

    @Test
    @DisplayName("a stretch of trail keeps its own character as it ages")
    void patternsDoNotCrawl() {
        Flight flight = widebodyAtCruise();
        TrailSettings settings = new TrailSettings();
        // Silence the parts that legitimately change with age -- fraying, the
        // sink, the meander -- so what is left is only the spreading rate, and
        // that is keyed to the exhaust rather than to how old it is.
        settings.shearVariationMs = 0.0;
        settings.vortexSinkM = 0.0;
        settings.crowOnsetSeconds = Double.MAX_VALUE / 4.0;
        settings.crowFullSeconds = Double.MAX_VALUE / 2.0;

        Trail earlier = sampler.sample(flight, 1200.0, true, true, settings);
        Trail later = sampler.sample(flight, 1320.0, true, true, settings);

        int compared = 0;
        for (TrailPoint a : earlier.points) {
            TrailPoint b = nearestByEmitTime(later, a.emitTime);
            // Right behind the aircraft the trail has barely started to spread,
            // so there is no factor to recover from its width.
            if (b == null || a.age < 60.0) {
                continue;
            }
            // Recover the factor the noise supplied. If it were hung on age
            // instead of emission time, the same exhaust would be spreading at a
            // different rate a frame later and the pattern would walk along the
            // trail at flying speed.
            double first = spreadFactor(a, flight, settings);
            double second = spreadFactor(b, flight, settings);
            // Relative, because the factor is not on a fixed scale: flights
            // differ in how fast their trails spread, so the same proportional
            // slack is a different absolute number from one to the next.
            //
            // A second of slack in the match costs a few per cent; hanging the
            // noise on age instead would move it by two minutes' worth, which is
            // most of the coarse octave's swing and nowhere near this bound.
            assertEquals(1.0, second / first, 0.10,
                    "the stretch emitted at " + a.emitTime + " changed how fast it spreads");
            compared++;
        }
        assertTrue(compared > 10, "not enough overlap to compare, saw " + compared);
    }

    private static TrailPoint nearestByEmitTime(Trail trail, double emitTime) {
        for (TrailPoint point : trail.points) {
            if (Math.abs(point.emitTime - emitTime) < 1.0) {
                return point;
            }
        }
        return null;
    }

    /** Undoes the width formula to recover the per-stretch spreading factor. */
    private static double spreadFactor(TrailPoint point, Flight flight, TrailSettings settings) {
        double wingspan = flight.type.wingspanM;
        double onsetAge = settings.onsetGapWingspans * wingspan / flight.groundSpeedMs;
        double grown = point.age - onsetAge;
        double initial = settings.initialHalfWidthWingspans * wingspan;
        return (point.halfWidth - initial) / (settings.spreadRateMPerSec * grown);
    }
}
