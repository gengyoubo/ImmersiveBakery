package github.com.gengyoubo.immersivebakery.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renyigesai.bakeries.block.moka_pot.MokaPotBlockEntity;
import com.renyigesai.bakeries.client.model.MokaPotModel;
import com.renyigesai.bakeries.client.render.block.MokaPotRender;
import github.com.gengyoubo.immersivebakery.client.MokaPotClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MokaPotRender.class, remap = false)
abstract class MokaPotRenderMixin {
    @Unique private static final String RENDER_ARGUMENTS = "(Lcom/renyigesai/bakeries/block/moka_pot/MokaPotBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";
    /** The lid's lower edge on the handle's side, model pixels, and how far it opens. */
    @Unique private static final float HINGE_X = 1.5F;
    @Unique private static final float HINGE_Y = -6.5F;
    @Unique private static final float OPEN_ANGLE = 105;

    @Shadow @Final private MokaPotModel<?> model;
    @Unique private float immersiveBakery$lidOpen;

    @Inject(method = {"render" + RENDER_ARGUMENTS, "m_6922_" + RENDER_ARGUMENTS}, at = @At("HEAD"))
    private void immersiveBakery$hideClosedPot(MokaPotBlockEntity pot, float partialTick, PoseStack pose,
                                               MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        immersiveBakery$lidOpen = pot.getLevel() == null ? 0 : MokaPotClient.lidOpen(pot);
        model.getAll().visible = immersiveBakery$lidOpen == 0;
    }

    @Inject(method = {"render" + RENDER_ARGUMENTS, "m_6922_" + RENDER_ARGUMENTS}, at = @At("RETURN"))
    private void immersiveBakery$drawOpenPot(MokaPotBlockEntity pot, float partialTick, PoseStack pose,
                                             MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        model.getAll().visible = true;
        float open = immersiveBakery$lidOpen;
        if (open == 0) {
            return;
        }
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(MokaPotRender.TEXTURE));
        pose.pushPose();
        // Same transform as MokaPotRender.render for a pot that does not shake.
        pose.translate(0.5F, 1.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-pot.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite().toYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(180));
        pose.scale(0.9995F, 0.9995F, 0.9995F);
        immersiveBakery$draw(pose, vertices, light, overlay, false);
        // The model stands on y 24 of its own space.
        pose.translate(HINGE_X / 16, (24 + HINGE_Y) / 16, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(OPEN_ANGLE * open));
        pose.translate(-HINGE_X / 16, -(24 + HINGE_Y) / 16, 0);
        immersiveBakery$draw(pose, vertices, light, overlay, true);
        pose.popPose();
    }

    @Unique
    private void immersiveBakery$draw(PoseStack pose, VertexConsumer vertices, int light, int overlay, boolean lid) {
        model.getAll().visit(pose, (part, path, index, cube) -> {
            // The lid is the only cube above the chamber.
            if ((path.isEmpty() && cube.minY < HINGE_Y) == lid) {
                cube.compile(part, vertices, light, overlay, 1, 1, 1, 1);
            }
        });
    }
}
