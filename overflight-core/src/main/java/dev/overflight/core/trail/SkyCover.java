package dev.overflight.core.trail;

import java.util.Arrays;
import java.util.List;

/**
 * Keeps trails from paving the sky.
 *
 * With busy traffic and damp air everywhere, every trail lasting as long as
 * the air lets it would leave the sky crosshatched from edge to edge. Rather
 * than cutting every trail short, which leaves a sky with no old trails in it
 * at all, this lets the youngest trails be and takes the oldest away first:
 * it finds the age below which all the trails together cover no more than the
 * allowed share of the sky, and fades out whatever is older than that.
 *
 * Cover is counted the way it would be seen, as width times length times
 * opacity, so a faint old wisp costs less than a dense young line.
 */
public final class SkyCover {

    private double[] ages = new double[256];
    private double[] covers = new double[256];

    /**
     * Fades the oldest parts of {@code trails} until they cover no more than
     * {@code budgetM2} square metres. Does nothing while they cover less.
     */
    public void limit(List<Trail> trails, double budgetM2) {
        int count = 0;
        double total = 0.0;
        for (int t = 0; t < trails.size(); t++) {
            List<TrailPoint> points = trails.get(t).points;
            for (int i = 0; i + 1 < points.size(); i++) {
                TrailPoint a = points.get(i);
                TrailPoint b = points.get(i + 1);
                double dx = b.x - a.x;
                double dz = b.z - a.z;
                double length = Math.sqrt(dx * dx + dz * dz);
                double cover = length * (a.halfWidth + b.halfWidth)
                        * 0.5 * (a.opacity + b.opacity);
                if (count == ages.length) {
                    ages = Arrays.copyOf(ages, count * 2);
                    covers = Arrays.copyOf(covers, count * 2);
                }
                ages[count] = 0.5 * (a.age + b.age);
                covers[count] = cover;
                total += cover;
                count++;
            }
        }
        if (total <= budgetM2 || count == 0) {
            return;
        }

        // Youngest first, adding up until the budget is spent.
        sortByAge(count);
        double spent = 0.0;
        double limitAge = ages[count - 1];
        for (int i = 0; i < count; i++) {
            spent += covers[i];
            if (spent > budgetM2) {
                limitAge = ages[i];
                break;
            }
        }

        // A soft edge: a quarter again as old, and at least a minute, to fade.
        double end = limitAge * 1.25 + 60.0;
        for (int t = 0; t < trails.size(); t++) {
            List<TrailPoint> points = trails.get(t).points;
            for (int i = 0; i < points.size(); i++) {
                TrailPoint p = points.get(i);
                p.opacity *= 1.0 - smoothstep(limitAge, end, p.age);
            }
        }
    }

    /** Heapsort of the first {@code n} entries by age, keeping covers paired; no boxing, no allocation. */
    private void sortByAge(int n) {
        for (int i = n / 2 - 1; i >= 0; i--) {
            siftDown(i, n);
        }
        for (int end = n - 1; end > 0; end--) {
            swap(0, end);
            siftDown(0, end);
        }
    }

    private void siftDown(int root, int n) {
        while (true) {
            int child = 2 * root + 1;
            if (child >= n) {
                return;
            }
            if (child + 1 < n && ages[child + 1] > ages[child]) {
                child++;
            }
            if (ages[root] >= ages[child]) {
                return;
            }
            swap(root, child);
            root = child;
        }
    }

    private void swap(int i, int j) {
        double a = ages[i];
        ages[i] = ages[j];
        ages[j] = a;
        double c = covers[i];
        covers[i] = covers[j];
        covers[j] = c;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = (x - edge0) / (edge1 - edge0);
        t = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return t * t * (3.0 - 2.0 * t);
    }
}
