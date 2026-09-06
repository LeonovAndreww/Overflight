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
        } catch (ReflectiveOperationException | RuntimeException e) {
            api = null;
            inUse = null;
        }
    }
}
