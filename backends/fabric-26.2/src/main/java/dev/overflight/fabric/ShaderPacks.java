package dev.overflight.fabric;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Whether a shader pack is drawing the world.
 *
 * The sky shell has to sit further out under a shader pack than without one. A
 * pack replaces Minecraft's fog with its own atmosphere and handles emissive
 * geometry at any distance, so the shell can be far enough away to fall behind
 * the pack's clouds. Vanilla simply blends anything past its fog into the fog
 * colour, and a trail out there comes out grey.
 *
 * Asked through reflection so Iris stays an optional dependency and the mod
 * builds and runs without it. If Iris is installed but the question cannot be
 * answered, the answer is assumed to be yes: guessing wrong that way leaves a
 * pack behaving exactly as it does today, while guessing the other way would
 * change what people are already looking at.
 */
public final class ShaderPacks {
    private static final String API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";

    private static boolean resolved;
    private static Object api;
    private static Method inUse;
    private static Method sunPathRotation;
    private static boolean irisPresent;

    private ShaderPacks() {}

    public static boolean inUse() {
        if (!resolved) {
            resolve();
        }
        if (inUse == null) {
            // Iris there but unreachable: assume a pack rather than change what
            // a pack user already sees.
            return irisPresent;
        }
        try {
            return Boolean.TRUE.equals(inUse.invoke(api));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return irisPresent;
        }
    }

    /**
     * How far the active pack tilts the sun's daily path, in degrees.
     *
     * Packs routinely move the sun off Minecraft's own overhead track -- a
     * Complementary Unbound default is -40, which drops the noon sun to fifty
     * degrees and swings it to one side -- and a trail lit from where vanilla
     * thinks the sun is would then be brightest in the wrong half of the sky.
     * Iris knows the number because it needs it for shadows, so it is asked
     * rather than guessed, and every pack that declares one is handled without
     * knowing anything about that pack.
     */
    public static float sunPathRotationDegrees() {
        if (!resolved) {
            resolve();
        }
        if (sunPathRotation == null || !inUse()) {
            return 0.0f;
        }
        try {
            Object value = sunPathRotation.invoke(api);
            return value instanceof Float ? (Float) value : 0.0f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0.0f;
        }
    }

    private static void resolve() {
        resolved = true;
        irisPresent = FabricLoader.getInstance().isModLoaded("iris");
        if (!irisPresent) {
            return;
        }
        try {
            Class<?> type = Class.forName(API_CLASS);
            api = type.getMethod("getInstance").invoke(null);
            inUse = type.getMethod("isShaderPackInUse");
            sunPathRotation = type.getMethod("getSunPathRotation");
        } catch (ReflectiveOperationException | RuntimeException e) {
            api = null;
            inUse = null;
            sunPathRotation = null;
        }
    }
}
