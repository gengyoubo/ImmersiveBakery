package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import github.com.gengyoubo.immersivebakery.kiln.Peel;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;

/** Draws a peel with what lies on its blade. */
public final class PeelRenderer extends BlockEntityWithoutLevelRenderer {
    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        private PeelRenderer renderer;

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) {
                renderer = new PeelRenderer();
            }
            return renderer;
        }
    };
    /** The middle of the blade's upper face in the peel's model, pixels. */
    static final float BLADE_X = 8.5F;
    static final float BLADE_Y = 27.5F;
    static final float BLADE_Z = 1;
    private static final float LOAD_SIZE = 9 / 16.0F;
    /** Bakeries shows what is in the kiln at half the size of an item frame. */
    private static final float KILN_SCALE = 0.5F;
    private static final Map<BakedModel, Bounds> BOUNDS = new WeakHashMap<>();

    private PeelRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack peel, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var renderer = Minecraft.getInstance().getItemRenderer();
        if (renderer.getModel(peel, null, null, 0) instanceof PeelModel.Loaded model) {
            pose.pushPose();
            pose.translate(0.5F, 0.5F, 0.5F);
            renderer.render(peel, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, model.empty());
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(BLADE_X / 16, BLADE_Y / 16, BLADE_Z / 16);
        // The load's up is the blade's outside.
        pose.mulPose(Axis.XP.rotationDegrees(90));
        drawLying(Peel.load(peel), LOAD_SIZE, pose, buffers, light, overlay);
        pose.popPose();
    }

    /** Draws an item lying on the origin, at most {@code size} wide: blocks and dough stand on their bottom, sprites lie. */
    static void drawLying(ItemStack stack, float size, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (stack.isEmpty()) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var renderer = minecraft.getItemRenderer();
        BakedModel model = model(stack);
        Bounds bounds = BOUNDS.computeIfAbsent(model, Bounds::of);
        float scale = scale(model, bounds, size);
        pose.pushPose();
        pose.scale(scale, scale, scale);
        if (model.isGui3d()) {
            pose.translate(0.5F - bounds.middleX, 0.5F - bounds.bottom, 0.5F - bounds.middleZ);
        } else {
            pose.translate(0, 0.5F / 16, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
        }
        renderer.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, model);
        pose.popPose();
    }

    /** Draws the block of an item standing on the origin in its own size, smaller only when it would not fit. */
    static void drawStanding(ItemStack stack, float mostWide, float mostHigh, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BakedModel model = model(stack);
        Bounds bounds = BOUNDS.computeIfAbsent(model, Bounds::of);
        float scale = Math.min(1, Math.min(mostWide / Math.max(0.01F, bounds.width()), mostHigh / Math.max(0.01F, bounds.sizeY)));
        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.translate(0.5F - bounds.middleX, 0.5F - bounds.bottom, 0.5F - bounds.middleZ);
        Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, model);
        pose.popPose();
    }

    /** How much an item drawn so takes up: across, up and deep. */
    static Vector3f lyingSize(ItemStack stack, float size) {
        BakedModel model = model(stack);
        Bounds bounds = BOUNDS.computeIfAbsent(model, Bounds::of);
        float scale = scale(model, bounds, size);
        return model.isGui3d() ? new Vector3f(bounds.sizeX, bounds.sizeY, bounds.sizeZ).mul(scale)
                : new Vector3f(bounds.sizeX, bounds.sizeZ, bounds.sizeY).mul(scale);
    }

    /** What a model takes up with its longest side so long: across, up and deep. */
    static Vector3f sizeOf(ItemStack stack, float longest) {
        Bounds bounds = BOUNDS.computeIfAbsent(model(stack), Bounds::of);
        return new Vector3f(bounds.sizeX, bounds.sizeY, bounds.sizeZ).mul(longest / Math.max(0.01F, Math.max(bounds.width(), bounds.sizeY)));
    }

    private static float scale(BakedModel model, Bounds bounds, float size) {
        return Math.min(KILN_SCALE * model.getTransforms().getTransform(ItemDisplayContext.FIXED).scale.x(), size / Math.max(0.01F, bounds.width()));
    }

    /** Bakeries' kiln shows the block of a pizza, not its flat icon. */
    private static BakedModel model(ItemStack stack) {
        var minecraft = Minecraft.getInstance();
        return stack.getItem() instanceof BlockItem item
                ? minecraft.getBlockRenderer().getBlockModel(item.getBlock().defaultBlockState())
                : minecraft.getItemRenderer().getModel(stack, minecraft.level, null, 0);
    }

    /** What a model takes up, blocks. */
    private record Bounds(float middleX, float bottom, float middleZ, float sizeX, float sizeY, float sizeZ) {
        float width() {
            return Math.max(sizeX, sizeZ);
        }

        static Bounds of(BakedModel model) {
            float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            RandomSource random = RandomSource.create(0);
            for (int side = 0; side <= 6; side++) {
                for (var quad : model.getQuads(null, side == 6 ? null : Direction.from3DDataValue(side), random, ModelData.EMPTY, null)) {
                    int[] vertices = quad.getVertices();
                    int stride = vertices.length / 4;
                    for (int vertex = 0; vertex < 4; vertex++) {
                        for (int axis = 0; axis < 3; axis++) {
                            float at = Float.intBitsToFloat(vertices[vertex * stride + axis]);
                            low[axis] = Math.min(low[axis], at);
                            high[axis] = Math.max(high[axis], at);
                        }
                    }
                }
            }
            return high[0] < low[0] ? new Bounds(0.5F, 0, 0.5F, 1, 1, 1)
                    : new Bounds((low[0] + high[0]) / 2, low[1], (low[2] + high[2]) / 2, high[0] - low[0], high[1] - low[1], high[2] - low[2]);
        }
    }
}
