package dev.overflight.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

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

	public OverflightCommon() {
		AirspaceNet.register();
		AirspaceCommands.register();
		if (FMLEnvironment.dist == Dist.CLIENT) {
			OverflightClient.init();
			ClientHooks.register();
		}
	}
}
