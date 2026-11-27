package dev.overflight.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MeshBufferTest {
    private static void add(MeshBuffer b, float x) {
        b.quad(new float[] {x, 0, 0}, new float[] {x, 1, 0}, new float[] {x, 1, 1}, new float[] {x, 0, 1},
                0f, 1f, 0f, 1f, 0.1f, 0.2f, 0.3f, 0.4f, 0.8f);
    }

    @Test
    void growsPastInitialCapacityKeepingEarlierQuads() {
        MeshBuffer b = new MeshBuffer();
        for (int i = 0; i < 1000; i++) {
            add(b, i);
        }
        assertEquals(1000, b.quadCount());
        assertEquals(4000, b.vertexCount());
        for (int i = 0; i < 1000; i += 137) {
            assertEquals(i, b.positions()[i * 12]);
            assertEquals(i, b.positions()[i * 12 + 9]);
        }
    }

    @Test
    void writesAlphaPerEdgeAndClearReusesStorage() {
        MeshBuffer b = new MeshBuffer();
        add(b, 1);
        assertEquals(0.4f, b.colours()[3]);
        assertEquals(0.4f, b.colours()[7]);
        assertEquals(0.8f, b.colours()[11]);
        assertEquals(0.8f, b.colours()[15]);
        b.clear();
        assertEquals(0, b.quadCount());
        add(b, 2);
        assertEquals(1, b.quadCount());
        assertEquals(2f, b.positions()[0]);
    }
}
