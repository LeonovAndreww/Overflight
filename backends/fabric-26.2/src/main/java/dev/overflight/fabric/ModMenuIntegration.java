package dev.overflight.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Hands the settings screen to Mod Menu.
 *
 * Reached only through the modmenu entrypoint, so this class and everything it
 * refers to are never loaded unless Mod Menu is installed.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return OverflightConfigScreen::create;
	}
}
