package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A lump of dough that is rolled out into a flat round, built of small steps as pixel art is. */
final class DoughPainter {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    private static final float LUMP_RADIUS = 2.5F;
    private static final float SHEET_RADIUS = 4.4F;
    private static final float LUMP_HEIGHT = 2.6F;
    private static final float SHEET_HEIGHT = 0.5F;
    /** The lump's layers: how far up they end, and how wide they are, rolled out and not. */
    private static final float[] TOPS = {0.45F, 0.8F, 1};
    private static final float[] LUMP_WIDTHS = {1, 0.88F, 0.6F};
    private static final float[] SHEET_WIDTHS = {1, 0.97F, 0.9F};
    private static final float[] SHADES = {0.97F, 1.03F, 1.1F};
    private static final float STEP = 0.5F;
    /** Anything smaller than a lump is built finer. */
    private static final float FINE_STEP = 0.25F;

    private DoughPainter() {
    }

    static float radius(float flatness) {
        return Mth.lerp(flatness, LUMP_RADIUS, SHEET_RADIUS);
    }

    static float height(float flatness) {
        return Mth.lerp(flatness, LUMP_HEIGHT, SHEET_HEIGHT);
    }

    /** On the origin; {@code scale} is that of its width alone. */
    static void draw(PoseStack pose, MultiBufferSource buffers, int light, int color, float flatness, float scale) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        float bottom = 0;
        for (int layer = 0; layer < TOPS.length; layer++) {
            float top = height(flatness) * TOPS[layer];
            float radius = radius(flatness) * scale * Mth.lerp(flatness, LUMP_WIDTHS[layer], SHEET_WIDTHS[layer]);
            disc(pose.last(), vertices, shaded(color, SHADES[layer]), light, radius, bottom, top);
            bottom = top;
        }
    }

    private static void disc(PoseStack.Pose pose, VertexConsumer vertices, int color, int light, float radius, float bottom, float top) {
        float step = radius < LUMP_RADIUS * LUMP_WIDTHS[LUMP_WIDTHS.length - 1] + STEP ? FINE_STEP : STEP;
        int rows = Math.max(2, Math.round(2 * radius / step));
        float deep = 2 * radius / rows;
        int side = shaded(color, 0.84F);
        int front = shaded(color, 0.76F);
        for (int row = 0; row < rows; row++) {
            float z0 = -radius + row * deep;
            float z1 = z0 + deep;
            float middle = z0 + deep / 2;
            float x = Math.max(step / 2, Math.round(Mth.sqrt(Math.max(0, radius * radius - middle * middle)) / step) * step);
            quad(pose, vertices, color, light, -x, top, z0, -x, top, z1, x, top, z1, x, top, z0);
            quad(pose, vertices, front, light, -x, bottom, z0, -x, top, z0, x, top, z0, x, bottom, z0);
            quad(pose, vertices, front, light, x, bottom, z1, x, top, z1, -x, top, z1, -x, bottom, z1);
            quad(pose, vertices, side, light, -x, bottom, z1, -x, top, z1, -x, top, z0, -x, bottom, z0);
            quad(pose, vertices, side, light, x, bottom, z0, x, top, z0, x, top, z1, x, bottom, z1);
        }
    }

    private static int shaded(int color, float shade) {
        int red = Mth.clamp(Math.round((color >> 16 & 0xFF) * shade), 0, 255);
        int green = Mth.clamp(Math.round((color >> 8 & 0xFF) * shade), 0, 255);
        int blue = Mth.clamp(Math.round((color & 0xFF) * shade), 0, 255);
        return red << 16 | green << 8 | blue;
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
