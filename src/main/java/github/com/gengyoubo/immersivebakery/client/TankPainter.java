package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pockyl.diegesis.api.client.StationView;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;

/** A tank's own block model, lit as the block, with its lid drawn apart. */
final class TankPainter {
    /** All of the lid is higher than the neck. */
    private static final float LID = 13.99F / 16;
    /** The lid's lower edge at the back of the neck, scene pixels, and how far it opens. */
    private static final float HINGE_Y = 14;
    private static final float HINGE_Z = 12;
    private static final float OPEN_ANGLE = 100;
    private static final long SEED = 42;
    private static final Map<BakedModel, Parts> PARTS = new WeakHashMap<>();

    private TankPainter() {
    }

    private record Parts(BakedModel jar, BakedModel lid) {
    }

    /** In scene pixels, from the scene's origin; {@code open} from 0 to 1. */
    static void draw(StationView view, BlockState tank, PoseStack pose, MultiBufferSource buffers, float open) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(tank);
        Parts parts = PARTS.computeIfAbsent(model, whole -> new Parts(new Part(whole, false), new Part(whole, true)));
        draw(view, tank, parts.jar, pose, buffers);
        pose.pushPose();
        pose.translate(0, HINGE_Y, HINGE_Z);
        pose.mulPose(Axis.XP.rotationDegrees(OPEN_ANGLE * open));
        pose.translate(0, -HINGE_Y, -HINGE_Z);
        draw(view, tank, parts.lid, pose, buffers);
        pose.popPose();
    }

    /** Any block model, from the scene's origin. */
    static void draw(StationView view, BlockState tank, BakedModel model, PoseStack pose, MultiBufferSource buffers) {
        pose.pushPose();
        pose.scale(16, 16, 16);
        // The scene is turned to the player, the block is not.
        pose.translate(0.5F, 0, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(view.facing().toYRot() - 180));
        pose.translate(-0.5F, 0, -0.5F);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().tesselateBlock(view.level(), model, tank, view.pos(), pose,
                buffers.getBuffer(RenderType.cutout()), false, RandomSource.create(), SEED, OverlayTexture.NO_OVERLAY, ModelData.EMPTY,
                RenderType.cutout());
        pose.popPose();
    }

    /** The lid of the model or all but it. */
    private static final class Part extends BakedModelWrapper<BakedModel> {
        private final boolean lid;

        private Part(BakedModel whole, boolean lid) {
            super(whole);
            this.lid = lid;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random, ModelData data,
                                        @Nullable RenderType type) {
            return super.getQuads(state, side, random, data, type).stream().filter(quad -> isLid(quad) == lid).toList();
        }

        private static boolean isLid(BakedQuad quad) {
            int[] vertices = quad.getVertices();
            for (int at = IQuadTransformer.POSITION; at < vertices.length; at += IQuadTransformer.STRIDE) {
                if (Float.intBitsToFloat(vertices[at + 1]) < LID) {
                    return false;
                }
            }
            return !quad.getDirection().equals(Direction.UP) || Float.intBitsToFloat(vertices[IQuadTransformer.POSITION + 1]) > LID + 1.0F / 16;
        }
    }
}
