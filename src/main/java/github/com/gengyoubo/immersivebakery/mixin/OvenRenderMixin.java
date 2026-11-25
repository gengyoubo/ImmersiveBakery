package github.com.gengyoubo.immersivebakery.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renyigesai.bakeries.block.oven.OvenBlockEntity;
import com.renyigesai.bakeries.client.model.OvenModel;
import com.renyigesai.bakeries.client.render.block.OvenRender;
import com.renyigesai.bakeries.init.BakeriesItemTag;
import github.com.gengyoubo.immersivebakery.client.OvenClient;
import github.com.gengyoubo.immersivebakery.client.Ripening;
import github.com.gengyoubo.immersivebakery.client.WorkBlockItemRenderer;
import github.com.gengyoubo.immersivebakery.oven.OvenHeat;
import github.com.gengyoubo.immersivebakery.oven.OvenStation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OvenRender.class, remap = false)
abstract class OvenRenderMixin {
    @Shadow @Final private OvenModel<?> model;
    @Unique private float immersiveBakery$increaseRestZ;
    @Unique private float immersiveBakery$decreaseRestZ;

    @Inject(method = "renderBlock", at = @At("HEAD"))
    private void immersiveBakery$pressButtons(OvenBlockEntity oven, float partialTick, PoseStack pose,
                                               MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        var increase = model.getButton1();
        var decrease = model.getButton2();
        immersiveBakery$increaseRestZ = increase.z;
        immersiveBakery$decreaseRestZ = decrease.z;
        increase.z += OvenClient.buttonTravel(oven, OvenStation.INCREASE);
        decrease.z += OvenClient.buttonTravel(oven, OvenStation.DECREASE);
    }

    @Inject(method = "renderBlock", at = @At("RETURN"))
    private void immersiveBakery$restoreButtons(OvenBlockEntity oven, float partialTick, PoseStack pose,
                                                 MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        model.getButton1().z = immersiveBakery$increaseRestZ;
        model.getButton2().z = immersiveBakery$decreaseRestZ;
    }

    @Redirect(method = "renderItem", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC, args = "array=get",
            target = "Lcom/renyigesai/bakeries/client/render/block/OvenRender;YS:[F"))
    private float immersiveBakery$levelRackItems(float[] offsets, int slot) {
        // Bakeries staggers adjacent slots vertically. Use the rack's base height for each slot.
        return 0.0F;
    }

    @Shadow
    private void renderModel(ItemStack stack, OvenBlockEntity oven, PoseStack pose, MultiBufferSource buffers, int overlay, int seed) {
        throw new AssertionError();
    }

    @Redirect(method = "renderItem", at = @At(value = "INVOKE", target = "Lcom/renyigesai/bakeries/client/render/block/OvenRender;renderModel("
            + "Lnet/minecraft/world/item/ItemStack;Lcom/renyigesai/bakeries/block/oven/OvenBlockEntity;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private void immersiveBakery$drawRaw(OvenRender renderer, ItemStack stack, OvenBlockEntity oven, PoseStack pose, MultiBufferSource buffers,
                                         int overlay, int seed) {
        MultiBufferSource faded = buffers;
        for (int slot = 0; slot < oven.getContainerSize(); slot++) {
            if (oven.getItem(slot) == stack) {
                faded = Ripening.inOven(oven, slot, ((OvenHeat) oven).immersiveBakery$mostTemperature(slot)).fadeRaw(buffers);
            }
        }
        renderModel(stack, oven, pose, faded, overlay, seed);
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void immersiveBakery$drawBaked(OvenBlockEntity oven, float partialTick, PoseStack pose, MultiBufferSource buffers, int overlay,
                                            CallbackInfo ci) {
        for (int slot = 0; slot < oven.getContainerSize(); slot++) {
            Ripening.Stage stage = Ripening.inOven(oven, slot, ((OvenHeat) oven).immersiveBakery$mostTemperature(slot));
            if (stage.isVisible()) {
                pose.pushPose();
                Ripening.bringCloser(pose);
                // Same transform as OvenRender.renderItem.
                pose.translate(OvenRender.VEC2S[slot].x, slot > 2 ? 0.34375F : 0.59375F, OvenRender.VEC2S[slot].y);
                pose.mulPose(Axis.YP.rotationDegrees(stage.product().is(BakeriesItemTag.UPRIGHT_ON_OVEN) ? 90 : 0));
                pose.scale(0.35F, 0.35F, 0.35F);
                renderModel(stage.product(), oven, pose, stage.fade(buffers), overlay, (int) oven.getBlockPos().asLong());
                pose.popPose();
            }
        }
    }

    @Inject(method = "renderModel", at = @At(value = "INVOKE", target = WorkBlockItemRenderer.IS_BLOCK_MODEL),
            cancellable = true)
    private void immersiveBakery$renderItemModel(ItemStack stack, OvenBlockEntity oven, PoseStack pose,
                                                 MultiBufferSource buffers, int overlay, int seed, CallbackInfo ci) {
        if (WorkBlockItemRenderer.renderThreeDimensionalItem(stack, oven, pose, buffers, overlay, seed + 1)) {
            ci.cancel();
        }
    }
}
