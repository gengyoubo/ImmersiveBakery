package github.com.gengyoubo.immersivebakery.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renyigesai.bakeries.block.fermentation_box.FermentationBoxBlockEntity;
import com.renyigesai.bakeries.client.render.block.FermentationBoxRender;
import github.com.gengyoubo.immersivebakery.client.WorkBlockItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FermentationBoxRender.class, remap = false)
abstract class FermentationBoxRenderMixin {
    @Inject(method = "renderModel", at = @At(value = "INVOKE", target = WorkBlockItemRenderer.IS_BLOCK_MODEL),
            cancellable = true)
    private void immersiveBakery$renderItemModel(ItemStack stack, FermentationBoxBlockEntity box, PoseStack pose,
                                                 MultiBufferSource buffers, int overlay, int seed, CallbackInfo ci) {
        if (WorkBlockItemRenderer.renderThreeDimensionalItem(stack, box, pose, buffers, overlay, seed + 1)) {
            ci.cancel();
        }
    }
}
