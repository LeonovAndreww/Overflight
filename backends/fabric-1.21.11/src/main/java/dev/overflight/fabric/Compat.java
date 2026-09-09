package dev.overflight.fabric;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * The handful of names that moved between game versions, for 1.21.11.
 *
 * All six of these are the same method doing the same thing as on 26.x under a
 * different name. Mojang's own renames account for two of them and Fabric's for
 * the rest; none of them is a behavioural difference. Keeping them here is what
 * lets every other file in the mod be shared verbatim.
 */
final class Compat {

	/** Sky light and block light both at maximum. */
	static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

	private Compat() {}

	/**
	 * Ticks since the world began, on the overworld's clock.
	 *
	 * 26.x names this getOverworldClockTime, having split it from the plain day
	 * time it used to share a method with.
	 */
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

	/** Where a payload sent from a server to its clients is declared. */
	static PayloadTypeRegistry<RegistryFriendlyByteBuf> clientboundPlayPayloads() {
		return PayloadTypeRegistry.playS2C();
	}
}
