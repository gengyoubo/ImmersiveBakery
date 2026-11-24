package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws batter solid. Diegesis' own liquids are see-through and drawn before block entities, so the blender's bowl, which a
 * block entity renderer draws, would cover them.
 */
final class BatterPainter {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    private static final int CELLS = 8;
    private static final float CONTRAST = 0.1F;
    private static final float STREAM_HALF_WIDTH = 0.28F;

    private BatterPainter() {
    }

    /** A square surface round the node's origin whose pixels swirl by {@code angle}; {@code mixed} evens out what is not stirred yet. */
    static void drawSurface(PoseStack pose, MultiBufferSource buffers, int light, float halfSize, int color, float angle, float mixed) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        float cell = 2 * halfSize / CELLS;
        for (int row = 0; row < CELLS; row++) {
            for (int column = 0; column < CELLS; column++) {
                float x = -halfSize + column * cell;
                float z = -halfSize + row * cell;
                float middleX = x + cell / 2;
                float middleZ = z + cell / 2;
                float swirl = Mth.sin(2 * (float) Mth.atan2(middleZ, middleX) + 2.4F * Mth.sqrt(middleX * middleX + middleZ * middleZ) - 2 * angle);
                float grain = ((row * 7 + column * 13 + row * column * 5) % 8) / 7.0F - 0.5F;
                // In steps, as pixel art is shaded.
                float shade = 1 + CONTRAST * Math.round(2 * Mth.lerp(0.35F + 0.65F * mixed, 2 * grain, swirl)) / 2.0F;
                quad(pose.last(), vertices, shaded(color, shade), light,
                        x, 0, z, x, 0, z + cell, x + cell, 0, z + cell, x + cell, 0, z);
            }
        }
    }

    /** Liquid falling along the node's vertical axis. */
    static void drawStream(PoseStack pose, MultiBufferSource buffers, int light, float top, float bottom, int color) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        float w = STREAM_HALF_WIDTH;
        quad(pose.last(), vertices, color, light, w, top, -w, w, bottom, -w, -w, bottom, -w, -w, top, -w);
        quad(pose.last(), vertices, shaded(color, 0.9F), light, w, top, w, w, bottom, w, w, bottom, -w, w, top, -w);
        quad(pose.last(), vertices, shaded(color, 0.9F), light, -w, top, -w, -w, bottom, -w, -w, bottom, w, -w, top, w);
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
