package dev.overflight.fabric;

import dev.overflight.api.Overflight;
import dev.overflight.core.config.OverflightConfig;
import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.AircraftType;
import dev.overflight.core.traffic.Flight;
import dev.overflight.core.traffic.FlightRequests;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class OverflightClient implements ClientModInitializer {
	public static final String MOD_ID = OverflightCommon.MOD_ID;
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final AircraftCatalog CATALOG = AircraftCatalog.defaults();
	private static final double TICKS_PER_SECOND = 20.0;

	private static SkyRenderer renderer;

	@Override
	public void onInitializeClient() {
		OverflightConfig config = ConfigIo.load();
		renderer = new SkyRenderer(config);
		renderer.register();
		new OverflightCommands(renderer).register();
		Overflight.install(renderer);

		ClientPlayNetworking.registerGlobalReceiver(AirspacePayload.TYPE,
				(payload, context) -> context.client().execute(() -> receive(payload)));

		LOGGER.info("Overflight is watching the sky ({} preset, {} flights/hour)",
				config.preset, config.traffic.densityPerHour);
	}

	/**
	 * An operator put something in the sky. The request travels, not the
	 * aircraft: this client builds them itself, from the same code the server
	 * would have used, so everyone ends up with identical flights.
	 */
	private static void receive(AirspacePayload payload) {
		if (renderer == null) {
			return;
		}
		if (payload.action() == AirspacePayload.ACTION_CLEAR) {
			renderer.manualTraffic().clear();
			return;
		}

		AircraftType type = CATALOG.byId(payload.typeId());
		ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
		if (type == null || level == null) {
			return;
		}

		double timeS = level.getGameTime() / TICKS_PER_SECOND;
		Flight.Condition[] conditions = Flight.Condition.values();
		int index = payload.condition();
		Flight.Condition condition = index >= 0 && index < conditions.length
				? conditions[index] : Flight.Condition.NORMAL;

		// Seeded from the request rather than the clock, so every client names
		// the same aircraft the same thing and a later clear removes all of them.
		long idBase = ((long) payload.typeId().hashCode() << 20)
				^ Double.doubleToLongBits(payload.overX())
				^ (long) level.getGameTime();

		List<Flight> flights = FlightRequests.build(type, payload.overX(), payload.overZ(),
				payload.flightLevel(), payload.headingDeg(), condition,
				Math.max(1, payload.count()), payload.formation(), timeS, idBase);
		for (int i = 0; i < flights.size(); i++) {
			renderer.manualTraffic().add(flights.get(i));
		}
	}

	/** The live renderer, or null before the client has finished starting. */
	public static SkyRenderer renderer() {
		return renderer;
	}
}
