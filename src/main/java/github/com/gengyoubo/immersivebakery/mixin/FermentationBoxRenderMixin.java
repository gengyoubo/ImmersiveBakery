package github.com.gengyoubo.immersivebakery.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renyigesai.bakeries.block.fermentation_box.FermentationBoxBlockEntity;
import com.renyigesai.bakeries.client.model.FermentationBoxModel;
import com.renyigesai.bakeries.client.render.block.FermentationBoxRender;
import github.com.gengyoubo.immersivebakery.client.FermentationClient;
import github.com.gengyoubo.immersivebakery.client.Ripening;
import github.com.gengyoubo.immersivebakery.client.WorkBlockItemRenderer;
import github.com.gengyoubo.immersivebakery.fermentation.FermentationStation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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

@Mixin(value = FermentationBoxRender.class, remap = false)
abstract class FermentationBoxRenderMixin {
    @Shadow @Final private FermentationBoxModel<?> model;

    @Inject(method = "renderBlock", at = @At("HEAD"))
    private void immersiveBakery$prepareControls(FermentationBoxBlockEntity box, float partialTick, PoseStack pose,
                                                  MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        var needle = model.getAll().getChild("cube_r1");
        needle.x = 4.5F;
        needle.zRot = FermentationClient.needleAngle(box);
        // The knobs are cubes of the cabinet itself, so its cubes are drawn one by one while a knob moves.
        model.getAll().skipDraw = FermentationClient.buttonTravel(box, FermentationStation.DECREASE) > 0
                || FermentationClient.buttonTravel(box, FermentationStation.INCREASE) > 0;
    }

    @Inject(method = "renderBlock", at = @At("RETURN"))
    private void immersiveBakery$drawPressedKnobs(FermentationBoxBlockEntity box, float partialTick, PoseStack pose,
                                                   MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        var cabinet = model.getAll();
        if (!cabinet.skipDraw) {
            return;
        }
        cabinet.skipDraw = false;
        var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(FermentationBoxRender.TEXTURE));
        pose.pushPose();
        // Same transform as FermentationBoxRender.renderBlock.
        pose.translate(0.0F, 1.5F, 0.0F);
        pose.mulPose(Axis.XP.rotationDegrees(180.0F));
        pose.scale(0.9995F, 0.9995F, 0.9995F);
        cabinet.visit(pose, (part, path, index, cube) -> {
            if (!path.isEmpty()) {
                return;
            }
            pose.pushPose();
            pose.translate(0, 0, immersiveBakery$knobTravel(box, cube.minX, cube.minZ) / 16.0F);
            cube.compile(pose.last(), vertices, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
            pose.popPose();
        });
        pose.popPose();
    }

    @Unique
    private static float immersiveBakery$knobTravel(FermentationBoxBlockEntity box, float minX, float minZ) {
        // Only the two knobs reach the front edge of the block.
        if (minZ != -8.0F) {
            return 0;
        }
        return FermentationClient.buttonTravel(box, minX == -6.0F ? FermentationStation.DECREASE : FermentationStation.INCREASE);
    }

    @Redirect(method = "renderItem", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC, args = "array=get",
            target = "Lcom/renyigesai/bakeries/client/render/block/FermentationBoxRender;YS:[F"))
    private float immersiveBakery$levelRackItems(float[] offsets, int slot) {
        // Bakeries staggers adjacent slots vertically. Use the rack's base height for each slot.
        return 0.0F;
    }

    @Shadow
    private void renderModel(ItemStack stack, FermentationBoxBlockEntity box, PoseStack pose, MultiBufferSource buffers, int overlay, int seed) {
        throw new AssertionError();
    }

    @Redirect(method = "renderItem", at = @At(value = "INVOKE", target = "Lcom/renyigesai/bakeries/client/render/block/FermentationBoxRender;renderModel("
            + "Lnet/minecraft/world/item/ItemStack;Lcom/renyigesai/bakeries/block/fermentation_box/FermentationBoxBlockEntity;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private void immersiveBakery$drawRaw(FermentationBoxRender renderer, ItemStack stack, FermentationBoxBlockEntity box, PoseStack pose, MultiBufferSource buffers,
                                         int overlay, int seed) {
        MultiBufferSource faded = buffers;
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            if (box.getItem(slot) == stack) {
                faded = Ripening.inBox(box, slot).fadeRaw(buffers);
            }
        }
        renderModel(stack, box, pose, faded, overlay, seed);
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void immersiveBakery$drawRisen(FermentationBoxBlockEntity box, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                            int overlay, CallbackInfo ci) {
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            Ripening.Stage stage = Ripening.inBox(box, slot);
            if (stage.isVisible()) {
                pose.pushPose();
                Ripening.bringCloser(pose);
                // Same transform as FermentationBoxRender.renderItem.
                pose.translate(FermentationBoxRender.VEC2S[slot].x, slot > 2 ? 0.28125F : 0.59375F, FermentationBoxRender.VEC2S[slot].y);
                pose.scale(0.35F, 0.35F, 0.35F);
                renderModel(stage.product(), box, pose, stage.fade(buffers), overlay, (int) box.getBlockPos().asLong());
                pose.popPose();
            }
        }
    }

    @Inject(method = "renderModel", at = @At(value = "INVOKE", target = WorkBlockItemRenderer.IS_BLOCK_MODEL),
            cancellable = true)
    private void immersiveBakery$renderItemModel(ItemStack stack, FermentationBoxBlockEntity box, PoseStack pose,
                                                 MultiBufferSource buffers, int overlay, int seed, CallbackInfo ci) {
        if (WorkBlockItemRenderer.renderThreeDimensionalItem(stack, box, pose, buffers, overlay, seed + 1)) {
            ci.cancel();
        }
    }
}
