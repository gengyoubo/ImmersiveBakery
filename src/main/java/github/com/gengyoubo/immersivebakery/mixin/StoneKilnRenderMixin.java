package github.com.gengyoubo.immersivebakery.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renyigesai.bakeries.block.stone_kiln.StoneKilnBlockEntity;
import com.renyigesai.bakeries.client.render.block.StoneKilnBlockEntityRender;
import github.com.gengyoubo.immersivebakery.client.KilnBaking;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StoneKilnBlockEntityRender.class, remap = false)
abstract class StoneKilnRenderMixin {
    @Unique private static final String RENDER_ARGUMENTS = "(Lcom/renyigesai/bakeries/block/stone_kiln/StoneKilnBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

    @Inject(method = {"render" + RENDER_ARGUMENTS, "m_6922_" + RENDER_ARGUMENTS}, at = @At("RETURN"))
    private void immersiveBakery$drawBakedPart(StoneKilnBlockEntity kiln, float partialTick, PoseStack pose,
                                               MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        KilnBaking.draw(kiln, partialTick, pose, buffers, overlay);
    }
}
