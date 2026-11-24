package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;

/** The drink itself in the block model of a finished drink, without cup, lid and straw. */
final class DrinkLook {
    /** Bakeries' drinks stand in the model as a box of this size, pixels. */
    private static final float LOW = 5.99F / 16;
    private static final float HIGH = 10.01F / 16;
    private static final float BOTTOM = 1.25F / 16;
    private static final float TOP = 9.3F / 16;
    private static final float TALL = 6 / 16.0F;
    private static final float WIDE = 3.9F / 16;
    private static final float HEIGHT = 8;
    private static final Map<BakedModel, List<BakedQuad>> QUADS = new WeakHashMap<>();

    private DrinkLook() {
    }

    /** Whether the drink is known to look like something. */
    static boolean isKnown(ItemStack drink) {
        return !quads(drink).isEmpty();
    }

    /** From the middle of its bottom, in pixels, in a box that high and wide. */
    static void draw(ItemStack drink, PoseStack pose, MultiBufferSource buffers, int light, float width, float height, float alpha) {
        VertexConsumer vertices = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        pose.pushPose();
        pose.scale(16 * width / 4, 16 * height / HEIGHT, 16 * width / 4);
        pose.translate(-0.5F, -BOTTOM, -0.5F);
        for (BakedQuad quad : quads(drink)) {
            vertices.putBulkData(pose.last(), quad, 1, 1, 1, alpha, light, OverlayTexture.NO_OVERLAY, false);
        }
        pose.popPose();
    }

    private static List<BakedQuad> quads(ItemStack drink) {
        if (!(drink.getItem() instanceof BlockItem item)) {
            return List.of();
        }
        BlockState state = item.getBlock().defaultBlockState();
        return QUADS.computeIfAbsent(Minecraft.getInstance().getBlockRenderer().getBlockModel(state), model -> {
            List<BakedQuad> found = new ArrayList<>();
            RandomSource random = RandomSource.create(0);
            for (int side = 0; side <= 6; side++) {
                for (BakedQuad quad : model.getQuads(state, side == 6 ? null : Direction.from3DDataValue(side), random, ModelData.EMPTY, null)) {
                    if (isDrink(quad)) {
                        found.add(quad);
                    }
                }
            }
            return found;
        });
    }

    private static boolean isDrink(BakedQuad quad) {
        float[] low = {1, 1, 1};
        float[] high = {0, 0, 0};
        int[] vertices = quad.getVertices();
        for (int at = IQuadTransformer.POSITION; at < vertices.length; at += IQuadTransformer.STRIDE) {
            for (int axis = 0; axis < 3; axis++) {
                float value = Float.intBitsToFloat(vertices[at + axis]);
                low[axis] = Math.min(low[axis], value);
                high[axis] = Math.max(high[axis], value);
            }
        }
        boolean inside = low[0] > LOW && high[0] < HIGH && low[2] > LOW && high[2] < HIGH && high[1] < TOP;
        boolean side = high[1] - low[1] > TALL;
        boolean surface = quad.getDirection() == Direction.UP && high[0] - low[0] > WIDE && high[2] - low[2] > WIDE;
        return inside && (side && Math.max(high[0] - low[0], high[2] - low[2]) > WIDE || surface);
    }
}
