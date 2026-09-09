package dev.overflight.core.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shell is a re-projection, not a skybox.
 *
 * Everything is drawn at one radius from the camera, which sounds like it should
 * flatten the sky into a painted dome and take the parallax with it. It does not:
 * the projection keeps each point's direction from the camera exactly and only
 * changes how far along that direction it is drawn. A turboprop five kilometres
 * up therefore still swings across the sky far faster than an airliner three
 * hundred kilometres away, because the direction to the near one really does
 * change faster.
 *
 * These tests are here because that is not obvious from reading the code, and
 * because losing it would be a serious and very hard-to-notice regression.
 */
class SkyProjectionTest {

    private static final double SHELL = 512.0;
    private final SkyProjection projection = new SkyProjection(SHELL);

    /** Angle between the projected point and the true one, in degrees. */
    private double directionErrorDegrees(double px, double py, double pz,
                                         double camX, double camY, double camZ) {
        double[] out = new double[3];
        projection.project(px, py, pz, camX, camY, camZ, out);

        double tx = px - camX;
        double ty = py - camY;
        double tz = pz - camZ;
        double trueLength = Math.sqrt(tx * tx + ty * ty + tz * tz);
        double outLength = Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);

        double dot = (tx * out[0] + ty * out[1] + tz * out[2]) / (trueLength * outLength);
        return Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));
    }

    /** Where a point appears, as a unit direction from the camera. */
    private double[] bearing(double px, double py, double pz,
                             double camX, double camY, double camZ) {
        double[] out = new double[3];
        projection.project(px, py, pz, camX, camY, camZ, out);
        double length = Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
        return new double[]{out[0] / length, out[1] / length, out[2] / length};
    }

    private static double angleBetweenDegrees(double[] a, double[] b) {
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        return Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));
    }

    @Test
    @DisplayName("a point is drawn in exactly the direction it really lies")
    void directionIsExact() {
        for (double distance : new double[]{2000.0, 40000.0, 300000.0}) {
            for (double height : new double[]{4000.0, 10668.0, 18000.0}) {
                assertEquals(0.0,
                        directionErrorDegrees(distance, height, distance * 0.4, 0.0, 64.0, 0.0),
                        1.0e-9,
                        "direction changed at " + distance + " m out and " + height + " m up");
            }
        }
    }

    @Test
    @DisplayName("everything lands on the shell, whatever its real distance")
    void everythingSitsOnTheShell() {
        double[] out = new double[3];
        for (double distance : new double[]{500.0, 50000.0, 400000.0}) {
            double returned = projection.project(distance, 10668.0, 0.0, 0.0, 64.0, 0.0, out);
            double drawn = Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);

            assertEquals(SHELL, drawn, 1.0e-6);
            // The true distance comes back, because sizes depend on it.
            assertTrue(returned > distance * 0.99);
        }
    }

    @Test
    @DisplayName("parallax survives: the near aircraft swings, the far one barely moves")
    void parallaxIsPreserved() {
        // A minecart ride: a kilometre travelled east.
        double travel = 1000.0;

        // Both to the north, so the travel is across the line of sight rather
        // than along it -- moving straight at something barely turns it.
        // A turboprop five kilometres up and ten out.
        double[] nearBefore = bearing(0.0, 5000.0, -10000.0, 0.0, 64.0, 0.0);
        double[] nearAfter = bearing(0.0, 5000.0, -10000.0, travel, 64.0, 0.0);
        double nearSwing = angleBetweenDegrees(nearBefore, nearAfter);

        // An airliner at cruise, three hundred kilometres away.
        double[] farBefore = bearing(0.0, 10668.0, -300000.0, 0.0, 64.0, 0.0);
        double[] farAfter = bearing(0.0, 10668.0, -300000.0, travel, 64.0, 0.0);
        double farSwing = angleBetweenDegrees(farBefore, farAfter);

        assertTrue(nearSwing > 3.0,
                "the near aircraft should visibly move, swung " + nearSwing + " degrees");
        assertTrue(farSwing < 0.3,
                "the far one should barely shift, swung " + farSwing + " degrees");
        assertTrue(nearSwing > farSwing * 20.0,
                "near should outrun far by a wide margin: "
                        + nearSwing + " against " + farSwing);
    }

    @Test
    @DisplayName("the swing matches the geometry, not just the ordering")
    void parallaxHasTheRightMagnitude() {
        double travel = 1000.0;
        double distance = 20000.0;

        double[] before = bearing(0.0, 10668.0, -distance, 0.0, 64.0, 0.0);
        double[] after = bearing(0.0, 10668.0, -distance, travel, 64.0, 0.0);
        double swing = angleBetweenDegrees(before, after);

        // Moving sideways by d past something r away turns it through
        // atan(d / r), and nothing about drawing it on a shell should change that.
        double expected = Math.toDegrees(Math.atan2(travel,
                Math.sqrt(distance * distance + (10668.0 - 64.0) * (10668.0 - 64.0))));
        assertEquals(expected, swing, 0.05);
    }

    @Test
    @DisplayName("apparent size still falls off with distance")
    void sizeFollowsRealDistance() {
        double near = projection.scaleFor(10000.0);
        double far = projection.scaleFor(100000.0);

        assertEquals(10.0, near / far, 1.0e-9,
                "ten times further should be drawn ten times smaller");
    }
}
