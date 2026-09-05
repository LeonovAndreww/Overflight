package dev.overflight.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * The part that runs on a server as well as a client.
 *
 * Overflight is a client mod and stays useful as one: the ambient sky needs no
 * server at all. Installing it on a server adds one thing, the ability for an
 * operator to put an aircraft over everybody at once.
 */
public final class OverflightCommon implements ModInitializer {
	/** Here rather than on the client class, which a dedicated server never loads. */
	public static final String MOD_ID = "overflight";


	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay()
				.register(AirspacePayload.TYPE, AirspacePayload.CODEC);
		AirspaceCommands.register();
	}
}
