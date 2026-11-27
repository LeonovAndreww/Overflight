package dev.overflight.forge17;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

import dev.overflight.core.traffic.AircraftCatalog;
import dev.overflight.core.traffic.AircraftType;
import dev.overflight.core.traffic.Flight;
import dev.overflight.core.traffic.FlightRequests;

/**
 * The channel the airspace request travels on. The mod accepts any remote version (see {@link Overflight17}), so a
 * client without it can join a server with it and the other way round; the request is then simply not shown.
 */
public final class AirspaceNet {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(Overflight17.MODID);

    private static final AircraftCatalog CATALOG = AircraftCatalog.defaults();
    private static final int MAX_PER_REQUEST = 24;
    private static final int MAX_MANUAL_FLIGHTS = 256;

    /** Filled on the network thread, drained on the client thread by {@link #drain()}. */
    private static final Queue<AirspaceMessage> PENDING = new ConcurrentLinkedQueue<AirspaceMessage>();

    private AirspaceNet() {}

    static void register() {
        CHANNEL.registerMessage(ClientHandler.class, AirspaceMessage.class, 0, Side.CLIENT);
    }

    /** Only instantiated on the client. */
    public static final class ClientHandler implements IMessageHandler<AirspaceMessage, IMessage> {

        @Override
        public IMessage onMessage(AirspaceMessage message, MessageContext ctx) {
            PENDING.add(message);
            return null;
        }
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
