package github.com.gengyoubo.immersivebakery.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renyigesai.bakeries.block.toaster.ToasterBlockEntity;
import com.renyigesai.bakeries.client.render.block.ToasterBlockEntityRender;
import github.com.gengyoubo.immersivebakery.client.Ripening;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ToasterBlockEntityRender.class, remap = false)
abstract class ToasterRenderMixin {
    @Inject(method = "startRender(Lcom/renyigesai/bakeries/block/toaster/ToasterBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("RETURN"))
    private void immersiveBakery$drawToasted(ToasterBlockEntity toaster, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                              int light, int overlay, CallbackInfo ci) {
        for (int slot = 0; slot < toaster.getItems().getSlots(); slot++) {
            Ripening.Stage stage = Ripening.inToaster(toaster, slot);
            if (stage.isVisible()) {
                pose.pushPose();
                Ripening.bringCloser(pose);
                // Same transform as ToasterBlockEntityRender.startRender.
                pose.translate(0, 0.5 + toaster.getProgress(partialTick) * 0.25, slot == 0 ? -0.09375F : 0.09375F);
                pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.scale(0.5F, 0.5F, 0.5F);
                Minecraft.getInstance().getItemRenderer().renderStatic(stage.product(), ItemDisplayContext.FIXED,
                        LevelRenderer.getLightColor(toaster.getLevel(), toaster.getBlockPos()), overlay, pose, stage.fade(buffers), toaster.getLevel(), 0);
                pose.popPose();
            }
        }
    }
}
