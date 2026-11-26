package dev.overflight.neoforge;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only glue that needs a loader API; kept out of the common entry point. */
final class ClientHooks {

	private ClientHooks() {}

	static void register(ModContainer container) {
		// The settings screen is built with Cloth Config, which is optional.
		if (ModList.get().isLoaded("cloth_config")) {
			container.registerExtensionPoint(IConfigScreenFactory.class,
					(minecraft, parent) -> OverflightConfigScreen.create(parent));
		}
	}
}
