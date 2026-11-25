package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;
import org.joml.Vector3f;

/** Draws whatever comes through it see-through. Items and blocks share the block atlas. */
final class Faded extends VertexConsumerWrapper {
    private final float alpha;
    @Nullable
    private final Vector3f normal;

    private Faded(VertexConsumer drawn, float alpha, @Nullable Vector3f normal) {
        super(drawn);
        this.alpha = alpha;
        this.normal = normal;
    }

    static MultiBufferSource of(MultiBufferSource buffers, float alpha) {
        return type -> new Faded(buffers.getBuffer(Sheets.translucentCullBlockSheet()), alpha, null);
    }

    /** For blocks, which are shaded already: {@code up} is the way up in the space of the pose, whence everything is fully lit. */
    static MultiBufferSource shaded(MultiBufferSource buffers, float alpha, Vector3f up) {
        return type -> new Faded(buffers.getBuffer(Sheets.translucentCullBlockSheet()), alpha, up);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return normal == null ? super.normal(x, y, z) : super.normal(normal.x, normal.y, normal.z);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int opacity) {
        return super.color(red, green, blue, Math.round(opacity * alpha));
    }
}
