package github.com.gengyoubo.immersivebakery.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renyigesai.bakeries.block.blender.BlenderBlockEntity;
import com.renyigesai.bakeries.client.render.block.BlenderRenderer;
import github.com.gengyoubo.immersivebakery.client.BlenderClient;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = BlenderRenderer.class, remap = false)
abstract class BlenderRendererMixin {
    @ModifyConstant(method = "startRender(Lcom/renyigesai/bakeries/block/blender/BlenderBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II)V", constant = @Constant(floatValue = -25.0F))
    private float immersiveBakery$clearTheBowl(float degrees, BlenderBlockEntity blender, float partialTick, PoseStack pose,
                                               MultiBufferSource buffers, int light, int overlay) {
        return BlenderClient.headAngle(blender, degrees);
    }
}
