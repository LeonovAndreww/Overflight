package dev.overflight.forge17;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.client.ClientCommandHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        Overflight17.LOG.info("Overflight client initialised");
        FMLCommonHandler.instance().bus().register(this);
        ClientCommandHandler.instance.registerCommand(new OverflightCommand());
        dev.overflight.api.Overflight.install(SkyRenderer.get());
        DevAutoShot.installIfAsked();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            AirspaceClient.drain();
        }
    }
}
