package dev.overflight.forge17;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void init(FMLInitializationEvent event) {
        AirspaceNet.register();
    }

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new AirspaceCommand());
    }
}
