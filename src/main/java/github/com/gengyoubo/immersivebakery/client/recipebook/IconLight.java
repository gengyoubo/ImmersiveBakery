/*
 * Taken from Hands-On Delight (https://github.com/Pocky-l/handson-delight), Copyright (c) 2026 Pocky, MIT License:
 * see META-INF/licenses/hands-on-delight.txt. Unchanged.
 */
package github.com.gengyoubo.immersivebakery.client.recipebook;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Items on a page are icons, lit as in the inventory whichever way the camera and the book turn. The shade the GUI's two lights
 * give each face is worked out here and multiplied into the colour; the normal then points into the level's light, where the
 * shader leaves the colour as it is. Lit by the level instead, an icon darkened as the camera or the book turned; on 1.20.1 the
 * icon's flipped scale also broke its normals ({@code PoseStack.scale} with one negative axis scales them by about 1e25).
 */
final class IconLight implements VertexConsumer {
    private static final Vector3f LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3f LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
    /** The GUI's lights in the icon's own frame: for block items ({@code Lighting.setupFor3DItems}) and flat ones. */
    private static final Vector3f[] BLOCK_LIGHTS = lights(new Matrix4f().rotationYXZ(1.0821041F, 3.2375858F, 0.0F)
            .rotateYXZ(-Mth.PI / 8.0F, 2.3561945F, 0.0F));
    private static final Vector3f[] FLAT_LIGHTS = lights(new Matrix4f().scaling(1.0F, -1.0F, 1.0F).rotateY(-Mth.PI / 8.0F).rotateX(2.3561945F));
    /** The entity shader's light: each light's share, and what lights every face. */
    private static final float POWER = 0.6F;
    private static final float AMBIENT = 0.4F;

    private final VertexConsumer delegate;
    private final Matrix3f toIcon;
    private final Vector3f[] lights;
    private final Vector3f lit;
    private final Vector3f face = new Vector3f();

    private IconLight(VertexConsumer delegate, Matrix3f toIcon, Vector3f[] lights, Vector3f lit) {
        this.delegate = delegate;
        this.toIcon = toIcon;
        this.lights = lights;
        this.lit = lit;
    }

    /**
     * Buffers for one icon. {@code facing}: the normal matrix the icon's model is drawn under, before the model's own GUI turn
     * (the icon's flip and depth must not be in it, as in the GUI); {@code blockLight}: the model's {@code usesBlockLight}.
     */
    static MultiBufferSource of(MultiBufferSource buffers, Matrix3f facing, boolean blockLight) {
        Matrix3f toIcon = new Matrix3f(facing).invert();
        Vector3f[] lights = blockLight ? BLOCK_LIGHTS : FLAT_LIGHTS;
        // The level's lights turn with the camera on 1.20.1 (Lighting.setupLevel with the camera's turn), as the normals do.
        Vector3f lit = RenderSystem.getInverseViewRotationMatrix().transformTranspose(new Vector3f(LIGHT_0));
        return type -> new IconLight(buffers.getBuffer(type), toIcon, lights, lit);
    }

    private static Vector3f[] lights(Matrix4f turn) {
        return new Vector3f[] {turn.transformDirection(new Vector3f(LIGHT_0)), turn.transformDirection(new Vector3f(LIGHT_1))};
    }

    /** What the GUI's lights make of a face with this normal, 0.4 to 1. */
    private float shade(float x, float y, float z) {
        toIcon.transform(face.set(x, y, z));
        float length = face.length();
        if (length < 1.0e-6F) {
            return 1.0F;
        }
        face.div(length);
        return Math.min(1.0F, POWER * (Math.max(0.0F, face.dot(lights[0])) + Math.max(0.0F, face.dot(lights[1]))) + AMBIENT);
    }

    @Override
    public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light,
            float normalX, float normalY, float normalZ) {
        float shade = shade(normalX, normalY, normalZ);
        delegate.vertex(x, y, z, red * shade, green * shade, blue * shade, alpha, u, v, overlay, light, lit.x(), lit.y(), lit.z());
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        // Built piece by piece, the colour comes before the normal: such a vertex keeps its colour and is fully lit.
        delegate.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(lit.x(), lit.y(), lit.z());
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        delegate.defaultColor(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}
