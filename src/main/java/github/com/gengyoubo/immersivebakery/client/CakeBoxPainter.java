package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;

/**
 * Bakeries' cake box in parts that move: the box, its lid with the handle, and the ribbon. The ribbon is only painted on the box, so it
 * is cut out of its faces, and what is beside it is painted on in its place.
 */
final class CakeBoxPainter {
    /** The ribbon goes over the middle of every side, this far each way, pixels. */
    private static final float MIDDLE = 8;
    private static final float HALF_RIBBON = 1;
    /** The sides are this far from the middle; the lid starts and ends at these heights. */
    private static final float SIDE = 6;
    private static final float BODY_TOP = 8.5F;
    private static final float LID_FROM = 9;
    private static final float LID_TOP = 11;
    private static final float LID_HINGE_Z = 14;
    private static final float LID_ANGLE = 112;
    /** The ribbon lies over the faces, and on the ground when it is untied. */
    private static final float OVER = 0.04F;
    /** The white frame under the lid: where the lid comes off it, and how thick it is. */
    private static final float LID_OFF = 10;
    private static final float FRAME_WIDE = 0.5F;
    private static final float WITHIN_PIXEL = 0.04F;
    private static final float FALLEN = -89;
    private static final Map<BakedModel, Parts> PARTS = new WeakHashMap<>();

    private CakeBoxPainter() {
    }

    /** A face of the model or a part of one: four places in pixels, and what of the texture is there. */
    private record Face(Vector3f[] places, float[] us, float[] vs, Direction side) {
        /** The part within bounds on an axis; what is painted there is taken from {@code shift} further along it. */
        Face within(int axis, float low, float high, float shift) {
            Vector3f[] cut = new Vector3f[4];
            float[] u = new float[4];
            float[] v = new float[4];
            for (int i = 0; i < 4; i++) {
                cut[i] = new Vector3f(places[i]);
                cut[i].setComponent(axis, Mth.clamp(cut[i].get(axis), low, high));
                Vector3f from = new Vector3f(cut[i]);
                // Not to its very edges, where the next pixel begins.
                from.setComponent(axis, Mth.clamp(from.get(axis), low + WITHIN_PIXEL, high - WITHIN_PIXEL) + shift);
                u[i] = at(us, from);
                v[i] = at(vs, from);
            }
            return new Face(cut, u, v, side);
        }

        private float at(float[] values, Vector3f place) {
            Vector3f one = new Vector3f(places[1]).sub(places[0]);
            Vector3f other = new Vector3f(places[3]).sub(places[0]);
            Vector3f off = new Vector3f(place).sub(places[0]);
            return values[0] + (values[1] - values[0]) * off.dot(one) / one.lengthSquared() + (values[3] - values[0]) * off.dot(other) / other.lengthSquared();
        }

        Face moved(int axis, float by) {
            Vector3f[] moved = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                moved[i] = new Vector3f(places[i]);
                moved[i].setComponent(axis, places[i].get(axis) + by);
            }
            return new Face(moved, us, vs, side);
        }

        /** Lying, from its upper edge on, that far along an axis. */
        Face laidOver(int axis, float by) {
            Vector3f[] laid = new Vector3f[4];
            float top = high(1);
            for (int i = 0; i < 4; i++) {
                laid[i] = new Vector3f(places[i]);
                if (places[i].y < top - 0.01F) {
                    laid[i].setComponent(axis, places[i].get(axis) + by);
                }
                laid[i].y = top;
            }
            // Seen from above, whichever way the side was painted.
            Vector3f up = new Vector3f(laid[1]).sub(laid[0]).cross(new Vector3f(laid[3]).sub(laid[0]));
            return up.y > 0 ? new Face(laid, us, vs, Direction.UP) : new Face(new Vector3f[] {laid[3], laid[2], laid[1], laid[0]},
                    new float[] {us[3], us[2], us[1], us[0]}, new float[] {vs[3], vs[2], vs[1], vs[0]}, Direction.UP);
        }

        float low(int axis) {
            return Math.min(Math.min(places[0].get(axis), places[1].get(axis)), Math.min(places[2].get(axis), places[3].get(axis)));
        }

        float high(int axis) {
            return Math.max(Math.max(places[0].get(axis), places[1].get(axis)), Math.max(places[2].get(axis), places[3].get(axis)));
        }

        boolean isNothing() {
            return new Vector3f(places[1]).sub(places[0]).cross(new Vector3f(places[3]).sub(places[0])).lengthSquared() < 1.0E-4F;
        }
    }

    private static final class Parts {
        private final List<Face> body = new ArrayList<>();
        private final List<Face> lid = new ArrayList<>();
        /** The ribbon down each side, and its piece on top that goes on from there. */
        private final Map<Direction, List<Face>> sides = new EnumMap<>(Direction.class);
        private final Map<Direction, List<Face>> tops = new EnumMap<>(Direction.class);

        private Parts(BlockState box, BakedModel model) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                sides.put(side, new ArrayList<>());
                tops.put(side, new ArrayList<>());
            }
            RandomSource random = RandomSource.create(0);
            for (int i = 0; i <= 6; i++) {
                for (BakedQuad quad : model.getQuads(box, i == 6 ? null : Direction.from3DDataValue(i), random, ModelData.EMPTY, null)) {
                    Face face = face(quad);
                    if (!face.isNothing()) {
                        sort(face);
                    }
                }
            }
        }

        /** Of a part of the white frame over the windows, which stays when the lid is taken off: it, as thick as it is, and its upper edge. */
        private void addFrame(Face frame) {
            if (frame.isNothing()) {
                return;
            }
            int axis = frame.side.getAxis() == Direction.Axis.X ? 0 : 2;
            float at = frame.low(axis);
            float inwards = at < MIDDLE ? FRAME_WIDE : -FRAME_WIDE;
            boolean outside = frame.side.getAxisDirection() == Direction.AxisDirection.POSITIVE == at > MIDDLE;
            body.add(outside ? frame : frame.moved(axis, inwards));
            if (outside) {
                body.add(frame.laidOver(axis, inwards));
            }
        }

        private void sort(Face face) {
            float low = face.low(1);
            float high = face.high(1);
            if (high > LID_TOP + 0.01F) {
                // The handle.
                lid.add(face);
            } else if (low >= LID_FROM - 1.01F && high <= LID_FROM + 0.01F && high > low) {
                // Between box and lid there is nothing but the ribbon.
                addToSide(face);
            } else if (face.side.getAxis() == Direction.Axis.Y) {
                boolean top = low > BODY_TOP;
                if (top && face.side == Direction.UP) {
                    sortTop(face);
                } else {
                    (top ? lid : body).add(face);
                }
            } else {
                int axis = face.side.getAxis() == Direction.Axis.X ? 2 : 0;
                List<Face> across = List.of(face.within(axis, 0, MIDDLE - HALF_RIBBON, 0), face.within(axis, MIDDLE - HALF_RIBBON, MIDDLE, -HALF_RIBBON),
                        face.within(axis, MIDDLE, MIDDLE + HALF_RIBBON, HALF_RIBBON), face.within(axis, MIDDLE + HALF_RIBBON, 16, 0));
                if (low < BODY_TOP) {
                    body.addAll(across);
                } else {
                    across.forEach(part -> {
                        addFrame(part.within(1, LID_FROM, LID_OFF, 0));
                        lid.add(part.within(1, LID_OFF, LID_TOP, 0));
                    });
                }
                addToSide(face.within(axis, MIDDLE - HALF_RIBBON, MIDDLE + HALF_RIBBON, 0));
            }
        }

        /** Only as it is seen from outside; loose, its inside is drawn as well. */
        private void addToSide(Face face) {
            int axis = face.side.getAxis() == Direction.Axis.X ? 0 : 2;
            boolean outside = face.side.getAxisDirection() == Direction.AxisDirection.POSITIVE == face.low(axis) > MIDDLE;
            if (outside) {
                sides.get(face.side).add(face);
            }
        }

        private void sortTop(Face top) {
            float[] cuts = {0, MIDDLE - HALF_RIBBON, MIDDLE, MIDDLE + HALF_RIBBON, 16};
            float[] shifts = {0, -HALF_RIBBON, HALF_RIBBON, 0};
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    lid.add(top.within(0, cuts[x], cuts[x + 1], shifts[x]).within(2, cuts[z], cuts[z + 1], shifts[z]));
                }
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                boolean alongZ = side.getAxis() == Direction.Axis.Z;
                boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
                // Two of them meet in the middle, the other two end at those.
                float inner = alongZ ? MIDDLE : positive ? MIDDLE + HALF_RIBBON : MIDDLE - HALF_RIBBON;
                tops.get(side).add(top.within(alongZ ? 0 : 2, MIDDLE - HALF_RIBBON, MIDDLE + HALF_RIBBON, 0)
                        .within(alongZ ? 2 : 0, positive ? inner : 0, positive ? 16 : inner, 0));
            }
        }
    }

    private static Face face(BakedQuad quad) {
        Vector3f[] places = new Vector3f[4];
        float[] us = new float[4];
        float[] vs = new float[4];
        int[] vertices = quad.getVertices();
        for (int i = 0; i < 4; i++) {
            int at = i * IQuadTransformer.STRIDE;
            int position = at + IQuadTransformer.POSITION;
            places[i] = new Vector3f(Float.intBitsToFloat(vertices[position]), Float.intBitsToFloat(vertices[position + 1]),
                    Float.intBitsToFloat(vertices[position + 2])).mul(16);
            us[i] = Float.intBitsToFloat(vertices[at + IQuadTransformer.UV0]);
            vs[i] = Float.intBitsToFloat(vertices[at + IQuadTransformer.UV0 + 1]);
        }
        return new Face(places, us, vs, quad.getDirection());
    }

    /** In scene pixels, from the scene's origin. {@code untied} and {@code open} from 0 to 1; untied, the ribbon lies round the box. */
    static void draw(BlockState box, PoseStack pose, MultiBufferSource buffers, int light, float untied, float open) {
        BlockState seen = box.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        Parts parts = PARTS.computeIfAbsent(Minecraft.getInstance().getBlockRenderer().getBlockModel(seen), model -> new Parts(seen, model));
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        parts.body.forEach(face -> draw(pose, vertices, light, face, 0));
        pose.pushPose();
        pose.translate(0, LID_OFF, LID_HINGE_Z);
        pose.mulPose(Axis.XP.rotationDegrees(LID_ANGLE * open));
        pose.translate(0, -LID_OFF, -LID_HINGE_Z);
        parts.lid.forEach(face -> draw(pose, vertices, light, face, 0));
        pose.popPose();

        VertexConsumer loose = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            float x = side.getStepX();
            float z = side.getStepZ();
            Axis axis = Axis.of(new Vector3f(-z, 0, x));
            pose.pushPose();
            // Falls over outwards from where the side stands.
            pose.translate(MIDDLE + x * SIDE, 0, MIDDLE + z * SIDE);
            pose.mulPose(axis.rotationDegrees(FALLEN * untied));
            pose.translate(-MIDDLE - x * SIDE, 0, -MIDDLE - z * SIDE);
            parts.sides.get(side).forEach(face -> draw(pose, loose, light, face, OVER));
            // What was on top comes after it.
            pose.translate(MIDDLE + x * SIDE, LID_TOP, MIDDLE + z * SIDE);
            pose.mulPose(axis.rotationDegrees(FALLEN * untied));
            pose.translate(-MIDDLE - x * SIDE, -LID_TOP, -MIDDLE - z * SIDE);
            parts.tops.get(side).forEach(face -> draw(pose, loose, light, face, OVER));
            pose.popPose();
        }
    }

    private static void draw(PoseStack pose, VertexConsumer vertices, int light, Face face, float over) {
        // Shaded as a block is, whichever way it is turned.
        int shade = Math.round(255 * switch (face.side) {
            case DOWN -> 0.5F;
            case UP -> 1;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
        });
        for (int i = 0; i < 4; i++) {
            Vector3f place = face.places[i];
            vertices.vertex(pose.last().pose(), place.x + over * face.side.getStepX(), place.y + over * face.side.getStepY(), place.z + over * face.side.getStepZ())
                    .color(shade, shade, shade, 255).uv(face.us[i], face.vs[i]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(pose.last().normal(), 0, 1, 0).endVertex();
        }
    }
}
