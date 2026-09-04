package dev.overflight.fabric;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.overflight.core.atmo.Isa;
import dev.overflight.core.atmo.SchmidtAppleman;

public class OverflightClient implements ClientModInitializer {
	public static final String MOD_ID = "overflight";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		// Smoke test that the shared core really is on the classpath and behaving:
		// a modern turbofan at FL350 should trail, one at FL250 should not.
		SchmidtAppleman.EngineProfile engine = SchmidtAppleman.EngineProfile.modernTurbofan();
		LOGGER.info("Overflight core online (FL350 trails: {}, FL250 trails: {})",
				trailsAt(350, engine), trailsAt(250, engine));
	}

	private static boolean trailsAt(int flightLevel, SchmidtAppleman.EngineProfile engine) {
		double altitude = Isa.flightLevelToMetres(flightLevel);
		return SchmidtAppleman.formsContrail(
				Isa.temperature(altitude), Isa.pressure(altitude), 0.45, engine);
	}
}
