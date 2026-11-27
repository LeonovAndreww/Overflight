package dev.overflight.forge;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;

/** Client-only glue that needs a loader API; kept out of the common entry point. */
final class ClientHooks {

	private ClientHooks() {}

	static void register() {
		// The settings screen is built with Cloth Config, which is optional.
		if (ModList.get().isLoaded("cloth_config")) {
			ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
					() -> new ConfigScreenHandler.ConfigScreenFactory(
							(minecraft, parent) -> OverflightConfigScreen.create(parent)));
		}
	}
}
