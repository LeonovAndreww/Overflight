package dev.overflight.fabric;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.LightCoordsUtil;

/**
 * The handful of names that moved between game versions, for 26.1 and 26.2.
 *
 * Every one of these is the same method doing the same thing under a different
 * name, so rather than fork the files that call them, each backend keeps its own
 * copy of this one. It is deliberately tiny: anything that grows past a rename
 * belongs in a shared class instead.
 */
final class Compat {

	/** Sky light and block light both at maximum. */
	static final int FULL_BRIGHT = LightCoordsUtil.FULL_BRIGHT;

	private Compat() {}

	/** Ticks since the world began, on the overworld's clock. */
	static long dayTime(ClientLevel level) {
		return level.getOverworldClockTime();
	}

	/** The world a client command was run in. */
	static ClientLevel levelOf(FabricClientCommandSource source) {
		return source.getLevel();
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
		return ClientCommands.literal(name);
	}

	static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(
			String name, ArgumentType<T> type) {
		return ClientCommands.argument(name, type);
	}

	/** Where a payload sent from a server to its clients is declared. */
	static PayloadTypeRegistry<RegistryFriendlyByteBuf> clientboundPlayPayloads() {
		return PayloadTypeRegistry.clientboundPlay();
	}
}
