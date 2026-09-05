package dev.overflight.fabric;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OverflightClient implements ClientModInitializer {
	public static final String MOD_ID = "overflight";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		new SkyRenderer().register();
		LOGGER.info("Overflight is watching the sky");
	}
}
