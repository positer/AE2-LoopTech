package com.example.ae2lightoptimizer.mixin;

import com.example.ae2lightoptimizer.client.OversizedItemViewport;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips an item preview whose texture would have a non-positive pixel size instead of letting the GPU device reject
 * the allocation and abort the client. Any screen may carry such a state, because the bounds can come from a client
 * mod's own item render state rather than from a vanilla model, so the guard is not restricted to one screen.
 */
@Mixin(PictureInPictureRenderer.class)
public abstract class OversizedItemViewportMixin {
    @Inject(method = "prepare", at = @At("HEAD"), cancellable = true)
    private void ae2lf$skipEmptyItemPreview(
            PictureInPictureRenderState renderState, GuiRenderState guiRenderState, int guiScale, CallbackInfo ci) {
        if (renderState instanceof OversizedItemRenderState item
                && !OversizedItemViewport.hasValidPixelSize(item.x0(), item.y0(), item.x1(), item.y1(), guiScale)) {
            // The frame has nothing to blit at this size, so skipping leaves the texture cache untouched.
            OversizedItemViewport.reportRejectedOnce(item.x0(), item.y0(), item.x1(), item.y1(), guiScale);
            ci.cancel();
        }
    }
}
