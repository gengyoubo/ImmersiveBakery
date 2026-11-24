package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import github.com.gengyoubo.immersivebakery.oven.OvenStation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class OvenPanel {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("bakeries", "textures/gui/oven_gui.png");
    private static final float TEXTURE_SIZE = 256;
    private static final float GAUGE_SCALE = 0.2F;
    private static final int FILL_HEIGHT = 38;

    private OvenPanel() {
    }

    static void drawTemperature(PoseStack pose, MultiBufferSource buffers, int currentTemperature) {
        int temperature = Mth.clamp(currentTemperature, 0, OvenStation.MAX_TEMPERATURE);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        // Sample the same frame and fill sprites as Bakeries' OvenScreen.
        blit(pose.last(), vertices, -1.2F, -4.8F, 0, 12 * GAUGE_SCALE, 48 * GAUGE_SCALE, 123, 19, 12, 48);
        int fill = temperature * FILL_HEIGHT / OvenStation.MAX_TEMPERATURE;
        if (fill > 0) {
            blit(pose.last(), vertices, -0.2F, (18 - fill) * GAUGE_SCALE, -0.08F,
                    2 * GAUGE_SCALE, fill * GAUGE_SCALE, 20, 166, 2, fill);
        }

        pose.pushPose();
        pose.translate(0, -5.9F, -0.08F);
        drawLabel(pose, buffers, temperature + " °C", 0.2F, 0xB02B2927);
        pose.popPose();
    }

    static void drawLabel(PoseStack pose, MultiBufferSource buffers, String label, float scale, int background) {
        Font font = Minecraft.getInstance().font;
        pose.pushPose();
        // Font puts glyphs in front of their shadow along +z, but a background behind its glyphs along -z.
        boolean shadow = background == 0;
        pose.scale(-scale, -scale, shadow ? -scale : scale);
        font.drawInBatch(label, -font.width(label) / 2.0F, -(font.lineHeight - 1) / 2.0F, 0xFFF2EDE4, shadow,
                pose.last().pose(), buffers, Font.DisplayMode.NORMAL, background, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void blit(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z,
                             float width, float height, int u, int v, int textureWidth, int textureHeight) {
        vertex(pose, vertices, x, y, z, u, v);
        vertex(pose, vertices, x, y + height, z, u, v + textureHeight);
        vertex(pose, vertices, x + width, y + height, z, u + textureWidth, v + textureHeight);
        vertex(pose, vertices, x + width, y, z, u + textureWidth, v);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z, int u, int v) {
        vertices.vertex(pose.pose(), -x, -y, z).color(255, 255, 255, 255).uv(u / TEXTURE_SIZE, v / TEXTURE_SIZE)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0, 0, -1).endVertex();
    }
}
