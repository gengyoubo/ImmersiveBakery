package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import github.com.gengyoubo.immersivebakery.cup.CupContents;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** What is in the drink cup: liquids in layers that shaking mixes, and solids lying on the bottom or floating. */
final class CupPainter {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    /** The inside of the glass, pixels. */
    private static final float HALF_WIDTH = 1.88F;
    private static final float FLOOR = 1.15F;
    private static final float OVER = 0.08F;
    private static final float PER_LIQUID = 2.3F;
    private static final float FULL = 8.2F;
    private static final float SOLID = 2.1F;
    private static final float SPREAD = 0.75F;
    /** Solids come this much out of the liquid, and lie this high on the bottom. */
    private static final float OUT = 0.1F;
    private static final float LYING = 0.9F;
    private static final float HEAP = 1.4F;

    private CupPainter() {
    }

    /** How high the finished drink stands over the floor of the glass. */
    static float full() {
        return FULL - FLOOR;
    }

    /** How high liquid stands over the floor of the glass, at rest. */
    static float level(List<ItemStack> in) {
        long liquids = in.stream().filter(CupContents::isPoured).count();
        return Math.min(FULL - FLOOR, liquids * PER_LIQUID);
    }

    /**
     * In the cup's own pixels, from the middle of its bottom.
     *
     * @param drink what it becomes
     * @param mixing how far it is mixed, 0 to 1
     * @param slosh how far liquid and solids are thrown up, pixels
     * @param time for the solids to tumble by, while {@code slosh} is not 0
     */
    static void draw(PoseStack pose, MultiBufferSource buffers, int light, List<ItemStack> in, float level, ItemStack drink, float mixing, float slosh,
                     float time) {
        List<ItemStack> liquids = in.stream().filter(CupContents::isPoured).toList();
        float surface = FLOOR + level;
        if (!liquids.isEmpty() && level > 0.05F) {
            VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
            float bottom = FLOOR;
            boolean known = DrinkLook.isKnown(drink);
            int mixed = known || drink.isEmpty() ? 0 : FoodColors.batter(drink, ItemStack.EMPTY);
            for (int i = 0; i < liquids.size(); i++) {
                float top = FLOOR + level * (i + 1) / liquids.size();
                int own = FoodColors.liquid(liquids.get(i));
                int color = known || drink.isEmpty() ? own : FastColor.ARGB32.lerp(mixing, own, mixed);
                // Thrown up, the uppermost layer fills more of the glass.
                box(pose.last(), vertices, light, color, bottom, i == liquids.size() - 1 ? Math.min(FULL, top + slosh) : top);
                bottom = top;
            }
            if (known && mixing > 0) {
                // The finished drink shows over what it is mixed of, the clearer the further it is.
                pose.pushPose();
                pose.translate(0, FLOOR, 0);
                DrinkLook.draw(drink, pose, buffers, light, 2 * HALF_WIDTH + OVER, Math.min(FULL - FLOOR, level + slosh) + OVER, mixing);
                pose.popPose();
            }
        }
        var renderer = Minecraft.getInstance().getItemRenderer();
        int solids = 0;
        int all = in.size() - liquids.size();
        for (ItemStack stack : in) {
            if (CupContents.isPoured(stack)) {
                continue;
            }
            float angle = Mth.TWO_PI * solids / Math.max(1, all) + 0.6F;
            float spread = all == 1 ? 0 : SPREAD;
            float up = liquids.isEmpty() ? FLOOR + LYING + (all > 2 && solids > 1 ? HEAP : 0) : Math.max(FLOOR + LYING, surface - SOLID / 2 + OUT);
            float tumble = slosh * Mth.sin(time * 9 + solids * 2.1F);
            boolean flat = !renderer.getModel(stack, null, null, 0).isGui3d();
            pose.pushPose();
            pose.translate(spread * Mth.cos(angle), Math.min(FULL, up + slosh * 0.6F + tumble * 0.4F), spread * Mth.sin(angle));
            pose.mulPose(Axis.YP.rotationDegrees(30 * solids + 12 + tumble * 30));
            pose.mulPose(Axis.XP.rotationDegrees((flat ? 60 : 14) + tumble * 40));
            float size = flat ? SOLID : SOLID * 2;
            pose.scale(size, size, size);
            renderer.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, Minecraft.getInstance().level, 0);
            pose.popPose();
            solids++;
        }
    }

    private static void box(PoseStack.Pose pose, VertexConsumer vertices, int light, int color, float bottom, float top) {
        float w = HALF_WIDTH;
        int side = FastColor.ARGB32.lerp(0.1F, color, 0);
        int front = FastColor.ARGB32.lerp(0.16F, color, 0);
        quad(pose, vertices, color, light, -w, top, -w, -w, top, w, w, top, w, w, top, -w);
        quad(pose, vertices, front, light, -w, bottom, -w, -w, top, -w, w, top, -w, w, bottom, -w);
        quad(pose, vertices, front, light, w, bottom, w, w, top, w, -w, top, w, -w, bottom, w);
        quad(pose, vertices, side, light, -w, bottom, w, -w, top, w, -w, top, -w, -w, bottom, -w);
        quad(pose, vertices, side, light, w, bottom, -w, w, top, -w, w, top, w, w, bottom, w);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices, int color, int light, float... corners) {
        for (int corner = 0; corner < 4; corner++) {
            vertices.vertex(pose.pose(), corners[corner * 3], corners[corner * 3 + 1], corners[corner * 3 + 2])
                    .color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, 255)
                    .uv(corner >= 2 ? 1 : 0, corner == 1 || corner == 2 ? 1 : 0)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(pose.normal(), 0, 1, 0).endVertex();
        }
    }
}
