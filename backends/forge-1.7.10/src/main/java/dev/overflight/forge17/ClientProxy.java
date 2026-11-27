package dev.overflight.forge17;

import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        Overflight17.LOG.info("Overflight client initialised");
        DevAutoShot.installIfAsked();
    }
}
