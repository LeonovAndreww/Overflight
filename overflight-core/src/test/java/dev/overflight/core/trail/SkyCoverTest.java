package dev.overflight.core.trail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyCoverTest {

    /** One trail of unit segments (cover 2 each at full opacity), ages 0, 100, 200... in the given order. */
    private static Trail trail(int n, boolean reversed) {
        return trail(n, reversed, 100.0);
    }

    private static Trail trail(int n, boolean reversed, double step) {
        List<TrailPoint> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            TrailPoint p = new TrailPoint();
            p.x = i;
            p.halfWidth = 1.0;
            p.opacity = 1.0;
            p.age = (reversed ? n - 1 - i : i) * step;
            pts.add(p);
        }
        return new Trail(null, pts, 1, 0.0, true, 0.0);
    }

    @Test
    @DisplayName("under budget nothing changes")
    void underBudget() {
        Trail t = trail(20, false);
        new SkyCover().limit(List.of(t), 1000.0);
        for (TrailPoint p : t.points) {
            assertEquals(1.0, p.opacity, 0.0);
        }
    }

    @Test
    @DisplayName("what is left after the fade fits the budget")
    void fadeFitsBudget() {
        Trail t = trail(400, false, 1.0);
        new SkyCover().limit(List.of(t), 200.0);
        double left = 0.0;
        for (int i = 0; i + 1 < t.points.size(); i++) {
            // width 2 x length 1 x mean opacity
            left += t.points.get(i).opacity + t.points.get(i + 1).opacity;
        }
        assertTrue(left <= 200.0 + 1.0e-6, "left " + left);
        assertTrue(left > 180.0, "not over-trimmed: " + left);
    }

    @Test
    @DisplayName("over budget the oldest go first, whatever the order they are stored in")
    void oldestFirst() {
        for (boolean reversed : new boolean[] {false, true}) {
            Trail t = trail(40, reversed);
            new SkyCover().limit(List.of(t), 10.0);
            TrailPoint youngest = null;
            TrailPoint oldest = null;
            for (TrailPoint p : t.points) {
                if (youngest == null || p.age < youngest.age) youngest = p;
                if (oldest == null || p.age > oldest.age) oldest = p;
            }
            assertEquals(1.0, youngest.opacity, 1.0e-9);
            assertEquals(0.0, oldest.opacity, 1.0e-9);
            for (TrailPoint a : t.points) {
                for (TrailPoint b : t.points) {
                    if (a.age < b.age) {
                        assertTrue(a.opacity >= b.opacity - 1.0e-9);
                    }
                }
            }
        }
    }
}
