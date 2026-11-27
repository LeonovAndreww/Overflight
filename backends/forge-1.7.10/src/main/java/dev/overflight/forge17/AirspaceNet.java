package dev.overflight.forge17;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * The channel the airspace request travels on. The mod accepts any remote version (see {@link Overflight17}), so a
 * client without it can join a server with it and the other way round; the request is then simply not shown.
 */
public final class AirspaceNet {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(Overflight17.MODID);

    private AirspaceNet() {}

    static void register() {
        CHANNEL.registerMessage(ClientHandler.class, AirspaceMessage.class, 0, Side.CLIENT);
    }

    /** Only instantiated on the client. */
    public static final class ClientHandler implements IMessageHandler<AirspaceMessage, IMessage> {

        @Override
        public IMessage onMessage(AirspaceMessage message, MessageContext ctx) {
            AirspaceClient.enqueue(message);
            return null;
        }
    }
}
