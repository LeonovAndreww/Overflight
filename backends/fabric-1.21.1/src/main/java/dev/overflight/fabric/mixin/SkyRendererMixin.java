package dev.overflight.fabric.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.overflight.fabric.OverflightClient;
import dev.overflight.fabric.SkyDraw;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the trails with the sky, at the very end of the game's sky rendering,
 * so that whatever is drawn over the sun is drawn over them too.
 *
 * The 1.21.1 form: the sky is a method of the level renderer, and the camera's
 * rotation arrives as an argument rather than being already applied, so it is
 * put on the model-view stack for the length of the draw.
 */
@Mixin(LevelRenderer.class)
abstract class SkyRendererMixin {

	@Inject(method = "renderSky", at = @At("TAIL"))
	private void overflight$drawTrails(Matrix4f frustumMatrix, Matrix4f projectionMatrix,
			float partialTick, Camera camera, boolean foggy, Runnable skyFogSetup,
			CallbackInfo info) {
		dev.overflight.fabric.SkyRenderer renderer = OverflightClient.renderer();
		if (renderer == null) {
			return;
		}
		org.joml.Matrix4fStack stack = RenderSystem.getModelViewStack();
		stack.pushMatrix();
		stack.mul(frustumMatrix);
		RenderSystem.applyModelViewMatrix();
		try {
			renderer.drawSky((mesh, texture) -> SkyDraw.draw(mesh, texture));
		} finally {
			stack.popMatrix();
			RenderSystem.applyModelViewMatrix();
		}
	}
}
