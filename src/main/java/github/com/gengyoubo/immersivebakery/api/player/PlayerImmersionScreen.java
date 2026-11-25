package github.com.gengyoubo.immersivebakery.api.player;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A non-pausing scene before the player, drawn over the live world in the place of their hands. The scene's space is in
 * blocks, upright as the world is: x to the player's right, y up, z towards the player, from the point {@link #origin} before
 * the eyes.
 */
public abstract class PlayerImmersionScreen extends Screen {
    private static final Vector3f ORIGIN = new Vector3f(0, -0.06F, -1);
    private static final float PITCH = 35;
    /** From where it comes up when it is opened. */
    private static final float BELOW = 0.7F;

    @Nullable
    private UUID token;
    /** From the screen, -1 to 1, into the scene; null before the first frame. */
    @Nullable
    private Matrix4f toScene;

    protected PlayerImmersionScreen(Component title) {
        super(title);
    }

    protected abstract void onState(CompoundTag state);

    /** Draws the tool and what is worked on. */
    protected void renderScene(Scene scene) {
    }

    /** How far down the player looks, degrees. */
    protected float viewPitch() {
        return PITCH;
    }

    /** Where the scene is, from the eyes: x to the right, y up, z back. */
    protected Vector3f origin() {
        return ORIGIN;
    }

    /** Where the mouse points on the level {@code height} of the scene. */
    protected final Optional<Vector3f> pointOn(double mouseX, double mouseY, float height) {
        return ray(mouseX, mouseY).flatMap(ray -> ray.onLevel(height));
    }

    /** Where the mouse points in the scene. */
    final Optional<Ray> ray(double mouseX, double mouseY) {
        if (toScene == null || width == 0 || height == 0) {
            return Optional.empty();
        }
        float x = (float) (2 * mouseX / width - 1);
        float y = (float) (1 - 2 * mouseY / height);
        Vector3f near = toScene.transformProject(new Vector3f(x, y, -1));
        return Optional.of(new Ray(near, toScene.transformProject(new Vector3f(x, y, 1)).sub(near)));
    }

    /** Away from the eyes. */
    record Ray(Vector3f from, Vector3f way) {
        Optional<Vector3f> onLevel(float height) {
            float share = (height - from.y) / way.y;
            return Math.abs(way.y) < 1.0E-5F || share < 0 ? Optional.empty() : Optional.of(new Vector3f(way).mul(share).add(from));
        }

        Ray in(Matrix4f space) {
            return new Ray(space.transformPosition(new Vector3f(from)), space.transformDirection(new Vector3f(way)));
        }
    }

    /** {@code shown} from 0, out of sight, to 1. */
    final void renderHands(RenderHandEvent event, float shown) {
        Vector3f origin = origin();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(origin.x, origin.y - BELOW * (1 - shown), origin.z);
        pose.mulPose(Axis.XP.rotationDegrees(viewPitch()));
        toScene = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(pose.last().pose()).invert();
        renderScene(new Scene(pose, event.getMultiBufferSource(), event.getPackedLight(), event.getPartialTick()));
        pose.popPose();
    }

    /** What a scene is drawn with; the pose stack is at its origin. */
    public record Scene(PoseStack pose, MultiBufferSource buffers, int light, float partialTick) {
    }

    protected final void sendAction(String action) {
        if (token != null && Minecraft.getInstance().screen == this) {
            PlayerImmersionNetwork.action(token, action);
        }
    }

    final void attach(UUID value) {
        token = value;
    }

    final boolean hasToken(UUID value) {
        return value.equals(token);
    }

    final void detach() {
        token = null;
    }

    @Override
    public void removed() {
        if (token != null && Minecraft.getInstance().getConnection() != null) {
            PlayerImmersionNetwork.requestClose(token);
        }
        detach();
        super.removed();
    }

    @Override
    public final boolean isPauseScreen() {
        return false;
    }
}
