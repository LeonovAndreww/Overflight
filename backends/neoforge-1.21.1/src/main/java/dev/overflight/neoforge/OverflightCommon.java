package dev.overflight.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The mod's entry point, on a server as well as a client.
 *
 * Overflight is a client mod and stays useful as one: the ambient sky needs no
 * server at all. Installing it on a server adds one thing, the ability for an
 * operator to put an aircraft over everybody at once.
 */
@Mod(OverflightCommon.MOD_ID)
public final class OverflightCommon {
	/** Here rather than on the client class, which a dedicated server never loads. */
	public static final String MOD_ID = "overflight";

	public OverflightCommon(IEventBus modBus, ModContainer container) {
		// Optional on both ends: a client without the mod can join a server with
		// it and the other way round; the request is simply not delivered.
		modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
			PayloadRegistrar registrar = event.registrar("1").optional();
			registrar.playToClient(AirspacePayload.TYPE, AirspacePayload.CODEC,
					(payload, context) -> context.enqueueWork(() -> OverflightClient.receive(payload)));
		});
		AirspaceCommands.register();
		if (FMLEnvironment.dist == Dist.CLIENT) {
			OverflightClient.init();
			ClientHooks.register(container);
		}
	}
}
