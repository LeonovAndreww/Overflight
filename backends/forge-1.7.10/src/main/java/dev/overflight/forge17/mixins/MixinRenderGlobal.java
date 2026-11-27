package dev.overflight.forge17.mixins;

import net.minecraft.client.renderer.RenderGlobal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.overflight.forge17.SkyHook;

/** Draws the trails at the end of the sky pass, so that anything drawn over the sun covers them too. */
@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {

    @Inject(method = "renderSky", at = @At("TAIL"))
    private void overflight$drawSky(float partialTicks, CallbackInfo ci) {
        SkyHook.onSkyEnd(partialTicks);
    }
}
