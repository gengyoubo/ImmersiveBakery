package github.com.gengyoubo.immersivebakery.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renyigesai.bakeries.block.cake_box.CakeBoxBlockEntity;
import com.renyigesai.bakeries.client.render.block.CakeBoxBlockEntityRender;
import com.renyigesai.bakeries.item.CustomCakeItem;
import github.com.gengyoubo.immersivebakery.client.CakeBoxClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CakeBoxBlockEntityRender.class, remap = false)
abstract class CakeBoxRenderMixin {
    @Unique private static final String RENDER_ARGUMENTS = "(Lcom/renyigesai/bakeries/block/cake_box/CakeBoxBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

    // Bakeries shows cakes at a quarter of their size in a box they fill.
    @Inject(method = {"render" + RENDER_ARGUMENTS, "m_6922_" + RENDER_ARGUMENTS}, at = @At("HEAD"), cancellable = true)
    private void immersiveBakery$drawInItsSize(CakeBoxBlockEntity box, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
                                                int overlay, CallbackInfo ci) {
        ItemStack cake = box.getInventory().getStackInSlot(0);
        if (cake.getItem() instanceof BlockItem && !(cake.getItem() instanceof CustomCakeItem)) {
            CakeBoxClient.drawCake(cake, box.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite(), pose, buffers, light, overlay);
            ci.cancel();
        }
    }
}
