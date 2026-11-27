package dev.overflight.forge17;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.client.ClientCommandHandler;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        Overflight17.LOG.info("Overflight client initialised");
        ClientCommandHandler.instance.registerCommand(new OverflightCommand());
        DevAutoShot.installIfAsked();
    }
}
