package dev.overflight.forge17;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

/** Forge 1.7.10 entry point. The simulation and the mesh builders come from overflight-core. */
@Mod(modid = Overflight17.MODID, version = Tags.VERSION, name = "Overflight", acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*", guiFactory = "dev.overflight.forge17.GuiFactory")
public class Overflight17 {

    public static final String MODID = "overflight";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "dev.overflight.forge17.ClientProxy", serverSide = "dev.overflight.forge17.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
