package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;

/** Draws whatever comes through it see-through. Items and blocks share the block atlas. */
final class Faded extends VertexConsumerWrapper {
    private final float alpha;

    private Faded(VertexConsumer drawn, float alpha) {
        super(drawn);
        this.alpha = alpha;
    }

    static MultiBufferSource of(MultiBufferSource buffers, float alpha) {
        return type -> new Faded(buffers.getBuffer(Sheets.translucentCullBlockSheet()), alpha);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int opacity) {
        return super.color(red, green, blue, Math.round(opacity * alpha));
    }
}
