package dev.overflight.forge;

import dev.overflight.api.Overflight;
import dev.overflight.core.config.OverflightConfig;
import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.AircraftType;
import dev.overflight.core.traffic.Flight;
import dev.overflight.core.traffic.FlightRequests;
import net.minecraft.client.multiplayer.ClientLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class OverflightClient {
	public static final String MOD_ID = OverflightCommon.MOD_ID;
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final AircraftCatalog CATALOG = AircraftCatalog.defaults();
	private static final double TICKS_PER_SECOND = 20.0;

	private static SkyRenderer renderer;

	/** Called once from the mod's constructor, on the client only. */
	static void init() {
		OverflightConfig config = ConfigIo.load();
		renderer = new SkyRenderer(config);
		SkyPipeline.register();
		renderer.register();
		new OverflightCommands(renderer).register();
		Overflight.install(renderer);
		// Dev-only screenshot hook: its class is stripped from release jars.
		try {
			java.lang.reflect.Method hook = Class.forName("dev.overflight.forge.DevAutoShot").getDeclaredMethod("installIfAsked");
			hook.setAccessible(true);
			hook.invoke(null);
		} catch (ClassNotFoundException ignored) {
			// not present in a release jar
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("DevAutoShot hook failed", e);
		}

		LOGGER.info("Overflight is watching the sky ({} preset, {} flights/hour)",
				config.preset, config.traffic.densityPerHour);
	}

	/** The most aircraft one request can put up; the command's own limit. */
	private static final int MAX_PER_REQUEST = 24;
	/** The most requested aircraft in the sky at once. */
	private static final int MAX_MANUAL_FLIGHTS = 256;

	/**
	 * An operator put something in the sky. The request travels, not the
	 * aircraft: this client builds them itself, from the same code the server
	 * would have used, so everyone ends up with identical flights.
	 */
	static void receive(AirspacePayload payload) {
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
		// The server is trusted to mean well, not to send sane numbers: one
		// count of two billion would have the client allocate until it froze.
		if (!Double.isFinite(payload.overX()) || !Double.isFinite(payload.overZ())
				|| !Double.isFinite(payload.headingDeg())
				|| renderer.manualTraffic().size() >= MAX_MANUAL_FLIGHTS) {
			return;
		}
		int count = Math.max(1, Math.min(payload.count(), MAX_PER_REQUEST));
		int flightLevel = Math.max(50, Math.min(payload.flightLevel(), 700));

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
				flightLevel, payload.headingDeg(), condition,
				count, payload.formation(), timeS, idBase);
		for (int i = 0; i < flights.size(); i++) {
			renderer.manualTraffic().add(flights.get(i));
		}
	}

	/** The live renderer, or null before the client has finished starting. */
	public static SkyRenderer renderer() {
		return renderer;
	}
}
