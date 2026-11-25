package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import github.com.gengyoubo.immersivebakery.api.player.PlayerImmersionScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Bakeries' sieve as it is held, what lies in it and what falls out of it. */
final class FlourSievePainter {
    /** Of the middle of the mesh in the item's model, blocks. */
    private static final float MESH_X = 0.5F;
    private static final float MESH_Y = 1.5F / 16;
    private static final float MESH_Z = 4.5F / 16;
    /** Light, so that it takes on any colour. */
    private static final ResourceLocation POWDER = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/white_concrete_powder.png");
    private static final float HEAP_RADIUS = 2.7F / 16;
    private static final float HEAP_HEIGHT = 1.3F / 16;
    private static final int HEAP_SIDES = 12;
    private static final int HEAP_RINGS = 4;
    private static final float GRAIN = 0.011F;
    private static final float GRAVITY = 0.9F;
    private static final float GRAIN_SECONDS = 1.1F;
    /** Not all of a stroke falls at once, seconds. */
    private static final float LATEST = 0.3F;
    /** How much finer the texture is than that of a block. */
    private static final float PIXELS = 1.6F;
    private static final float THING = 5 / 16.0F;
    private static final float MADE = 4 / 16.0F;
    /** A block is so much smaller than something flat. */
    private static final float SOLID = 0.6F;
    private static final int MOST_THINGS = 4;
    private static final int MOST_GRAINS = 700;

    private final RandomSource random = RandomSource.create();
    private final List<Grain> grains = new ArrayList<>();

    /** A bit of powder, in the scene. */
    private static final class Grain {
        private float x;
        private float y;
        private float z;
        private float speedX;
        private float speedY;
        private float speedZ;
        private float size;
        private float age;
    }

    /** Lets powder fall from a mesh that moves sideways so fast. */
    void fall(int count, float x, float y, float z, float speed) {
        for (int i = 0; i < count && grains.size() < MOST_GRAINS; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float distance = HEAP_RADIUS * 0.85F * Mth.sqrt(random.nextFloat());
            Grain grain = new Grain();
            grain.x = x + Mth.cos(angle) * distance;
            grain.y = y - 0.01F;
            grain.z = z + Mth.sin(angle) * distance;
            grain.speedX = speed * 0.25F + (random.nextFloat() - 0.5F) * 0.08F;
            grain.speedY = -0.05F - 0.1F * random.nextFloat();
            grain.speedZ = (random.nextFloat() - 0.5F) * 0.08F;
            grain.size = GRAIN * (0.6F + 0.9F * random.nextFloat());
            grain.age = -LATEST * random.nextFloat();
            grains.add(grain);
        }
    }

    void drawFalling(PlayerImmersionScreen.Scene scene, int color, float seconds) {
        VertexConsumer out = scene.buffers().getBuffer(RenderType.entityCutoutNoCull(POWDER));
        PoseStack.Pose pose = scene.pose().last();
        grains.removeIf(grain -> {
            grain.age += seconds;
            if (grain.age < 0) {
                return false;
            }
            grain.speedY -= GRAVITY * seconds;
            grain.x += grain.speedX * seconds;
            grain.y += grain.speedY * seconds;
            grain.z += grain.speedZ * seconds;
            float size = grain.size * (1 - 0.5F * grain.age / GRAIN_SECONDS);
            vertex(out, pose, grain.x - size, grain.y, grain.z, color, 1, scene.light());
            vertex(out, pose, grain.x, grain.y - size, grain.z, color, 1, scene.light());
            vertex(out, pose, grain.x + size, grain.y, grain.z, color, 1, scene.light());
            vertex(out, pose, grain.x, grain.y + size, grain.z, color, 1, scene.light());
            return grain.age > GRAIN_SECONDS;
        });
    }

    /** From the middle of its mesh; {@code turn} is where its handle points, degrees from towards the player. */
    static void drawSieve(PlayerImmersionScreen.Scene scene, ItemStack sieve, float turn) {
        Minecraft minecraft = Minecraft.getInstance();
        PoseStack pose = scene.pose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        // Items are drawn from the middle of their block.
        pose.translate(0.5F - MESH_X, 0.5F - MESH_Y, 0.5F - MESH_Z);
        minecraft.getItemRenderer().render(sieve, ItemDisplayContext.NONE, false, pose, scene.buffers(), scene.light(), OverlayTexture.NO_OVERLAY,
                minecraft.getItemRenderer().getModel(sieve, minecraft.level, minecraft.player, 0));
        pose.popPose();
    }

    /** On the mesh, from its middle; {@code amount} from 0 to 1, leaning to the side it was thrown to. */
    static void drawHeap(PlayerImmersionScreen.Scene scene, int color, float amount, float lean) {
        if (amount <= 0) {
            return;
        }
        VertexConsumer out = scene.buffers().getBuffer(RenderType.entityCutoutNoCull(POWDER));
        PoseStack.Pose pose = scene.pose().last();
        float wide = HEAP_RADIUS * (0.45F + 0.55F * amount);
        float high = HEAP_HEIGHT * amount;
        for (int ring = 0; ring < HEAP_RINGS; ring++) {
            for (int side = 0; side < HEAP_SIDES; side++) {
                corner(out, pose, ring, side, wide, high, lean, color, scene.light());
                corner(out, pose, ring + 1, side, wide, high, lean, color, scene.light());
                corner(out, pose, ring + 1, side + 1, wide, high, lean, color, scene.light());
                corner(out, pose, ring, side + 1, wide, high, lean, color, scene.light());
            }
        }
    }

    /** What is put on the mesh, lying there, in a few layers for many; {@code size} from 0 to 1. */
    static void drawThing(PlayerImmersionScreen.Scene scene, ItemStack thing, float size, int count, float lean) {
        if (size <= 0.02F) {
            return;
        }
        PoseStack pose = scene.pose();
        int shown = Math.min(MOST_THINGS, 1 + count / 16);
        for (int i = 0; i < shown; i++) {
            pose.pushPose();
            pose.translate(lean * 0.02F + (i % 2) * 0.02F, 0.006F + 0.022F * i, (i % 3) * 0.015F);
            pose.mulPose(Axis.YP.rotationDegrees(35 * i));
            // Only the top one, which is sifted, shrinks.
            drawItem(scene, thing, THING * (i == shown - 1 ? size : 1));
            pose.popPose();
        }
    }

    /** As it is in the inventory: what is flat there lies. */
    private static void drawItem(PlayerImmersionScreen.Scene scene, ItemStack thing, float size) {
        Minecraft minecraft = Minecraft.getInstance();
        PoseStack pose = scene.pose();
        BakedModel model = minecraft.getItemRenderer().getModel(thing, minecraft.level, minecraft.player, 0);
        pose.pushPose();
        if (model.isGui3d()) {
            size *= SOLID;
            pose.translate(0, size / 2, 0);
            pose.scale(size, size, size);
        } else {
            pose.scale(size, size, size);
            pose.translate(0, 0.5F / 16, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
        }
        minecraft.getItemRenderer().render(thing, ItemDisplayContext.NONE, false, pose, scene.buffers(), scene.light(), OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
    }

    /** What was sifted, falling out under the mesh; {@code age} from 0 to 1. */
    static void drawMade(PlayerImmersionScreen.Scene scene, ItemStack made, float x, float age) {
        PoseStack pose = scene.pose();
        pose.pushPose();
        pose.translate(x, -0.08F - 0.55F * age * age, 0.12F + 0.1F * age);
        pose.mulPose(Axis.XP.rotationDegrees(55 + 60 * age));
        drawItem(scene, made, MADE * Mth.clamp(5 * age, 0, 1));
        pose.popPose();
    }

    private static void corner(VertexConsumer out, PoseStack.Pose pose, int ring, int side, float wide, float high, float lean, int color, int light) {
        float share = (float) ring / HEAP_RINGS;
        float angle = side * Mth.TWO_PI / HEAP_SIDES;
        float up = 1 - share * share;
        // Not quite even, as powder is not; the same all round at the top and the edge.
        float rough = 1 + 0.12F * Mth.sin(side % HEAP_SIDES * 2.4F + ring * 1.7F) * 4 * share * (1 - share);
        float shade = 0.8F + 0.2F * up + 0.06F * Mth.sin(angle + 0.8F) * share;
        vertex(out, pose, Mth.cos(angle) * wide * share + lean * wide * 0.35F * up, 0.004F + high * up * rough, Mth.sin(angle) * wide * share,
                color, Math.min(1, shade), light);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, int color, float shade, int light) {
        out.vertex(pose.pose(), x, y, z).color((color >> 16 & 255) / 255.0F * shade, (color >> 8 & 255) / 255.0F * shade, (color & 255) / 255.0F * shade, 1)
                .uv(0.5F + x * PIXELS, 0.5F + z * PIXELS).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), 0, 1, 0).endVertex();
    }
}
