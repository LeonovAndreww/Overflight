package dev.overflight.forge;

import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The channel the airspace request travels on.
 *
 * Optional on both ends: every version string is accepted, so a client without
 * the mod can join a server with it and the other way round; the request is
 * simply not delivered.
 */
final class AirspaceNet {
	private static final ResourceLocation ID = new ResourceLocation(OverflightCommon.MOD_ID, "airspace");
	private static final String VERSION = "1";

	private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			ID, () -> VERSION, version -> true, version -> true);

	private AirspaceNet() {}

	static void register() {
		CHANNEL.registerMessage(0, AirspacePayload.class, AirspacePayload::write, AirspacePayload::read,
				AirspaceNet::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
	}

	private static void handle(AirspacePayload payload, Supplier<NetworkEvent.Context> context) {
		NetworkEvent.Context ctx = context.get();
		ctx.enqueueWork(() -> OverflightClient.receive(payload));
		ctx.setPacketHandled(true);
	}

	/** Whether this player's client has the channel, and so can be sent the request. */
	static boolean canReach(ServerPlayer player) {
		Connection connection = player.connection.connection;
		return CHANNEL.isRemotePresent(connection);
	}

	static void send(ServerPlayer player, AirspacePayload payload) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
	}
}
