package dev.overflight.fabric;

import dev.overflight.api.Overflight;
import dev.overflight.core.config.OverflightConfig;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OverflightClient implements ClientModInitializer {
	public static final String MOD_ID = "overflight";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static SkyRenderer renderer;

	@Override
	public void onInitializeClient() {
		OverflightConfig config = ConfigIo.load();
		renderer = new SkyRenderer(config);
		renderer.register();
		new OverflightCommands(renderer).register();
		Overflight.install(renderer);

		LOGGER.info("Overflight is watching the sky ({} preset, {} flights/hour)",
				config.preset, config.traffic.densityPerHour);
	}

	/** The live renderer, or null before the client has finished starting. */
	public static SkyRenderer renderer() {
		return renderer;
	}
}
