package dev.overflight.forge17;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.AircraftType;
import dev.overflight.core.traffic.Flight;
import dev.overflight.core.traffic.FlightRequests;

/**
 * The client half of {@link AirspaceNet}. Kept apart so a dedicated server never has to load (or verify) a class
 * that mentions {@code Minecraft} or {@code SkyRenderer}.
 */
final class AirspaceClient {

    private static final AircraftCatalog CATALOG = AircraftCatalog.defaults();
    private static final int MAX_PER_REQUEST = 24;
    private static final int MAX_MANUAL_FLIGHTS = 256;

    /** Filled on the network thread, drained on the client thread by {@link #drain()}. */
    private static final Queue<AirspaceMessage> PENDING = new ConcurrentLinkedQueue<AirspaceMessage>();

    private AirspaceClient() {}

    static void enqueue(AirspaceMessage message) {
        PENDING.add(message);
    }

    /** Applies the requests that arrived since the last tick. Called on the client thread. */
    static void drain() {
        AirspaceMessage message;
        while ((message = PENDING.poll()) != null) {
            receive(message);
        }
    }

    private static void receive(AirspaceMessage message) {
        SkyRenderer renderer = SkyRenderer.get();
        if (message.action == AirspaceMessage.ACTION_CLEAR) {
            renderer.manualTraffic().clear();
            return;
        }
        World world = Minecraft.getMinecraft().theWorld;
        AircraftType type = CATALOG.byId(message.typeId);
        if (type == null || world == null) {
            return;
        }
        // The server is trusted to mean well, not to send sane numbers.
        if (!finite(message.overX) || !finite(message.overZ)
            || !finite(message.headingDeg)
            || renderer.manualTraffic().size() >= MAX_MANUAL_FLIGHTS) {
            return;
        }
        int count = Math.max(1, Math.min(message.count, MAX_PER_REQUEST));
        int flightLevel = message.flightLevel <= 0 ? 0 : Math.max(50, Math.min(message.flightLevel, 700));
        double timeS = world.getTotalWorldTime() / 20.0;
        Flight.Condition[] conditions = Flight.Condition.values();
        Flight.Condition condition = message.condition >= 0 && message.condition < conditions.length
            ? conditions[message.condition]
            : Flight.Condition.NORMAL;
        long idBase = ((long) message.typeId.hashCode() << 20) ^ Double.doubleToLongBits(message.overX)
            ^ world.getTotalWorldTime();
        List<Flight> flights = FlightRequests.build(
            type,
            message.overX,
            message.overZ,
            flightLevel,
            message.headingDeg,
            condition,
            count,
            message.formation == null ? "line" : message.formation,
            timeS,
            idBase);
        for (Flight flight : flights) {
            renderer.manualTraffic().add(flight);
        }
        Overflight17.LOG.info("Airspace request received: " + flights.size() + " flight(s)");
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
