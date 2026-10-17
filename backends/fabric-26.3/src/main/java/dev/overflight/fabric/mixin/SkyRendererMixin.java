package dev.overflight.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.overflight.fabric.OverflightClient;
import dev.overflight.fabric.SkyDraw;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the trails with the sky, right after the sun, moon and stars, so that
 * whatever is drawn over the sun is drawn over them too. 26.3 draws its whole
 * sky in one render pass, handed in here, and the trails go into the same one.
 */
@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {

	@Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
	private void overflight$drawTrails(RenderPass pass, PoseStack poseStack, float a, float b,
			float c, MoonPhase phase, float d, float e, CallbackInfo info) {
		dev.overflight.fabric.SkyRenderer renderer = OverflightClient.renderer();
		if (renderer != null) {
			renderer.drawSky((mesh, texture) -> SkyDraw.draw(pass, mesh, texture));
		}
	}
}
