package dev.overflight.fabric;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientLevel;

/** The handful of names that differ between game versions, for 1.20.1. */
final class Compat {

	/** Sky light and block light both at maximum. */
	static final int FULL_BRIGHT = 0xF000F0;

	private Compat() {}

	/** Ticks since the world began, on the overworld's clock. */
	static long dayTime(ClientLevel level) {
		return level.getDayTime();
	}

	/** The world a client command was run in. */
	static ClientLevel levelOf(FabricClientCommandSource source) {
		return source.getWorld();
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
		return ClientCommandManager.literal(name);
	}

	static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(
			String name, ArgumentType<T> type) {
		return ClientCommandManager.argument(name, type);
	}

	/**
	 * The colours the game is painting the sky with, at the camera, as ARGB:
	 * the sunrise and sunset glow (its alpha the glow's strength), the sky,
	 * and the sky light. This version has no sky-light colour of its own, so
	 * the sky's colour lightened toward white stands in for it.
	 */
	static void skyColours(net.minecraft.client.multiplayer.ClientLevel level,
			net.minecraft.client.Camera camera, float partialTick, int[] out) {
		float[] glow = level.effects().getSunriseColor(level.getTimeOfDay(partialTick), partialTick);
		out[0] = glow == null ? 0 : argb(glow[3], glow[0], glow[1], glow[2]);
		net.minecraft.world.phys.Vec3 sky = level.getSkyColor(camera.getPosition(), partialTick);
		out[1] = argb(1.0f, (float) sky.x, (float) sky.y, (float) sky.z);
		out[2] = argb(1.0f, 0.5f + 0.5f * (float) sky.x, 0.5f + 0.5f * (float) sky.y,
				0.5f + 0.5f * (float) sky.z);
	}

	private static int argb(float a, float r, float g, float b) {
		return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int clamp(float v) {
		return Math.max(0, Math.min(255, Math.round(v * 255.0f)));
	}
}
