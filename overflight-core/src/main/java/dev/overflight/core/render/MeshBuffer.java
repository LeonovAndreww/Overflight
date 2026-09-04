package dev.overflight.core.render;

/**
 * A growable pile of quads, in the layout every backend needs anyway: four
 * vertices each carrying a position, a texture coordinate and a colour.
 *
 * Building geometry here rather than in a backend keeps the version-specific
 * code down to handing these numbers to a vertex consumer, and means the shape
 * of a trail can be checked without launching Minecraft.
 */
public final class MeshBuffer {
    private float[] positions = new float[3 * 4 * 64];
    private float[] uvs = new float[2 * 4 * 64];
    private float[] colours = new float[4 * 4 * 64];
    private int quadCount;

    public int quadCount() {
        return quadCount;
    }

    public int vertexCount() {
        return quadCount * 4;
    }

    public float[] positions() {
        return positions;
    }

    public float[] uvs() {
        return uvs;
    }

    /** Red, green, blue, alpha per vertex, each 0 to 1. */
    public float[] colours() {
        return colours;
    }

    public void clear() {
        quadCount = 0;
    }

    /**
     * Adds one quad. Vertices are given in order around the face; the caller is
     * responsible for winding them consistently.
     */
    public void quad(float[] v0, float[] v1, float[] v2, float[] v3,
                     float u0, float u1, float vLow, float vHigh,
                     float r, float g, float b, float aLow, float aHigh) {
        ensure(quadCount + 1);
        int p = quadCount * 12;
        int t = quadCount * 8;
        int c = quadCount * 16;

        write(p, v0);
        write(p + 3, v1);
        write(p + 6, v2);
        write(p + 9, v3);

        uvs[t] = u0;     uvs[t + 1] = vLow;
        uvs[t + 2] = u1; uvs[t + 3] = vLow;
        uvs[t + 4] = u1; uvs[t + 5] = vHigh;
        uvs[t + 6] = u0; uvs[t + 7] = vHigh;

        colour(c, r, g, b, aLow);
        colour(c + 4, r, g, b, aLow);
        colour(c + 8, r, g, b, aHigh);
        colour(c + 12, r, g, b, aHigh);

        quadCount++;
    }

    private void write(int at, float[] xyz) {
        positions[at] = xyz[0];
        positions[at + 1] = xyz[1];
        positions[at + 2] = xyz[2];
    }

    private void colour(int at, float r, float g, float b, float a) {
        colours[at] = r;
        colours[at + 1] = g;
        colours[at + 2] = b;
        colours[at + 3] = a;
    }

    private void ensure(int quads) {
        if (quads * 12 <= positions.length) {
            return;
        }
        int grown = Math.max(quads, positions.length / 12 * 2);
        positions = grow(positions, grown * 12);
        uvs = grow(uvs, grown * 8);
        colours = grow(colours, grown * 16);
    }

    private static float[] grow(float[] source, int length) {
        float[] copy = new float[length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }
}
