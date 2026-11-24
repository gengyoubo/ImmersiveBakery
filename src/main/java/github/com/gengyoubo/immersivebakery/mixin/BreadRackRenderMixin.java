package github.com.gengyoubo.immersivebakery.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renyigesai.bakeries.block.bread_rack.BreadRackBlockEntity;
import com.renyigesai.bakeries.client.render.block.BreadRackRender;
import github.com.gengyoubo.immersivebakery.client.WorkBlockItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BreadRackRender.class, remap = false)
abstract class BreadRackRenderMixin {
    // Bakeries leaves a quarter of the rack behind the open window.
    @ModifyConstant(method = "renderDoor", constant = {@Constant(floatValue = 12.0F), @Constant(floatValue = -12.0F)})
    private float immersiveBakery$openFully(float distance) {
        return Math.copySign(16.0F, distance);
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = WorkBlockItemRenderer.IS_BLOCK_MODEL),
            cancellable = true)
    private void immersiveBakery$renderItemModel(ItemStack stack, BreadRackBlockEntity rack, Vec2 position,
                                                 PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                                 int slot, CallbackInfo ci) {
        int seed = (int) rack.getBlockPos().asLong();
        if (WorkBlockItemRenderer.renderThreeDimensionalItem(stack, rack, pose, buffers, overlay, seed)) {
            // The rack pushes its slot transform in this method, before rendering the item.
            pose.popPose();
            ci.cancel();
        }
    }
}
