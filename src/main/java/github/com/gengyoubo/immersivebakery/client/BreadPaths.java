package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renyigesai.bakeries.block.MouldToastBlock;
import github.com.gengyoubo.immersivebakery.bread.BreadCutting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;

/** Where the knife has to go on bread, in pixels of its model: dashed until it was there, a groove then. */
final class BreadPaths {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    /** The seam between bread and mould. */
    private static final float MOULD_X0 = 4;
    private static final float MOULD_X1 = 12;
    private static final float MOULD_Z0 = 6;
    private static final float MOULD_Z1 = 10;
    private static final float MOULD_RIM = 4.5F;
    private static final float CAKE_RIM = 7.5F;
    private static final float STEP = 0.25F;
    /** How much higher the line of sight is a pixel nearer to the player, over a cake. */
    private static final float SIGHT_RISE = 2.1F;
    /** Lines look like those of Hands-On Delight: so wide, and dashes and gaps of that many steps. */
    private static final float HALF_WIDTH = 0.16F;
    private static final int DASH = 2;
    private static final int DASH_AND_GAP = 4;
    private static final int LINE = 0xFFFFFFFF;
    /** Round the dashes, to be seen on whatever is light. */
    private static final int OUTLINE = 0xE0281206;
    private static final float OUTLINE_WIDTH = 0.13F;
    private static final float SIDE_WIDER = 1.7F;
    private static final int GROOVE = 0xE0402412;
    private static final float OVER = 0.04F;
    /** The knife cuts what is so near its point. */
    private static final float REACH = 0.8F;
    /** A path is done when this much of it is: nobody must hit the very ends. */
    private static final float ENOUGH = 0.94F;

    private final List<Path> paths = new ArrayList<>();

    /** One closed seam or one straight line. */
    static final class Path {
        /** The line's number, 0 for the seam of the mould. */
        final int line;
        private final List<Vector3f> points = new ArrayList<>();
        private boolean[] cut;
        private int left;
        /** On this side of the cake and not on top; null for all others. */
        @Nullable
        private Direction side;

        private Path(int line) {
            this.line = line;
        }

        /** Whether the path goes along x at a point, else along z. Of two neighbours the next is on the same side, but for the last. */
        private boolean isAlongX(int point) {
            Vector3f at = points.get(point);
            Vector3f next = points.get(point + 1 < points.size() ? point + 1 : point - 1);
            return Math.abs(next.x - at.x) > Math.abs(next.z - at.z);
        }

        private void closed() {
            cut = new boolean[points.size()];
            left = points.size();
        }

        boolean isDone() {
            return left <= points.size() * (1 - ENOUGH);
        }

        private boolean pass(Pointer from, Pointer to, float share) {
            boolean any = false;
            for (int i = 0; i < cut.length; i++) {
                Vector3f point = points.get(i);
                Vector3f a = cut[i] ? null : from.at(point.y);
                Vector3f b = cut[i] ? null : to.at(point.y);
                if (a != null && b != null && Mth.square(point.x - Mth.lerp(share, a.x, b.x)) + Mth.square(point.z - Mth.lerp(share, a.z, b.z))
                        < REACH * REACH) {
                    cut[i] = true;
                    left--;
                    any = true;
                }
            }
            return any;
        }

        private void complete() {
            Arrays.fill(cut, true);
            left = 0;
        }
    }

    /** The paths on this bread, where the lines of {@code done}, a bit each, are cut already. */
    static BreadPaths of(BlockState bread, int done) {
        BreadPaths made = new BreadPaths();
        if (BreadCutting.isInMould(bread)) {
            boolean cake = !(bread.getBlock() instanceof MouldToastBlock);
            float x0 = cake ? BreadCutting.CAKE_FROM : MOULD_X0;
            float x1 = cake ? BreadCutting.CAKE_TO : MOULD_X1;
            float z0 = cake ? BreadCutting.CAKE_FROM : MOULD_Z0;
            float z1 = cake ? BreadCutting.CAKE_TO : MOULD_Z1;
            float y = cake ? CAKE_RIM : MOULD_RIM;
            Path seam = new Path(0);
            side(seam, y, x0, z0, x1, z0);
            side(seam, y, x1, z0, x1, z1);
            side(seam, y, x1, z1, x0, z1);
            side(seam, y, x0, z1, x0, z0);
            seam.closed();
            made.paths.add(seam);
        } else if (BreadCutting.isCuttable(bread)) {
            List<float[]> tops = tops(bread);
            for (int number : BreadCutting.lines(bread)) {
                float at = BreadCutting.position(bread, number);
                if (BreadCutting.isLayered(bread)) {
                    // Layers are parted from any side.
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        made.paths.add(onSide(number, side, at));
                    }
                    continue;
                }
                Path line = new Path(number);
                boolean across = BreadCutting.isAcross(bread, number);
                for (float along = STEP / 2; along < 16; along += STEP) {
                    float x = across ? along : at;
                    float z = across ? at : along;
                    float y = height(tops, x, z);
                    if (y > 0 && !isHidden(tops, x, y, z)) {
                        line.points.add(new Vector3f(x, y, z));
                    }
                }
                line.closed();
                if ((done & 1 << number) != 0) {
                    line.complete();
                }
                made.paths.add(line);
            }
        }
        return made;
    }

    private static Path onSide(int number, Direction side, float height) {
        Path line = new Path(number);
        line.side = side;
        for (float along = BreadCutting.CAKE_FROM + STEP / 2; along < BreadCutting.CAKE_TO; along += STEP) {
            float out = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? BreadCutting.CAKE_TO : BreadCutting.CAKE_FROM;
            line.points.add(side.getAxis() == Direction.Axis.X ? new Vector3f(out, height, along) : new Vector3f(along, height, out));
        }
        line.closed();
        return line;
    }

    private static void side(Path path, float y, float x0, float z0, float x1, float z1) {
        int steps = Math.round((Math.abs(x1 - x0) + Math.abs(z1 - z0)) / STEP);
        for (int i = 0; i < steps; i++) {
            float at = (i + 0.5F) / steps;
            path.points.add(new Vector3f(Mth.lerp(at, x0, x1), y, Mth.lerp(at, z0, z1)));
        }
    }

    /** The upper faces of the model as the scene sees it: x0, z0, x1, z1 and their height. */
    private static List<float[]> tops(BlockState bread) {
        BlockState seen = bread.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? bread.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH) : bread;
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(seen);
        List<float[]> tops = new ArrayList<>();
        for (Direction side : new Direction[] {null, Direction.UP}) {
            for (BakedQuad quad : model.getQuads(seen, side, RandomSource.create(0), ModelData.EMPTY, null)) {
                if (quad.getDirection() != Direction.UP) {
                    continue;
                }
                float[] top = {16, 16, 0, 0, 0};
                int[] vertices = quad.getVertices();
                for (int at = IQuadTransformer.POSITION; at < vertices.length; at += IQuadTransformer.STRIDE) {
                    float x = 16 * Float.intBitsToFloat(vertices[at]);
                    float z = 16 * Float.intBitsToFloat(vertices[at + 2]);
                    top[0] = Math.min(top[0], x);
                    top[1] = Math.min(top[1], z);
                    top[2] = Math.max(top[2], x);
                    top[3] = Math.max(top[3], z);
                    top[4] = 16 * Float.intBitsToFloat(vertices[at + 1]);
                }
                tops.add(top);
            }
        }
        return tops;
    }

    /** Whether something higher stands between it and the camera. */
    private static boolean isHidden(List<float[]> tops, float x, float y, float z) {
        for (float nearer = STEP; nearer < z; nearer += STEP) {
            if (height(tops, x, z - nearer) > y + nearer * SIGHT_RISE + 0.1F) {
                return true;
            }
        }
        return false;
    }

    private static float height(List<float[]> tops, float x, float z) {
        float height = 0;
        for (float[] top : tops) {
            if (x >= top[0] - 1.0E-3F && x <= top[2] + 1.0E-3F && z >= top[1] && z <= top[3]) {
                height = Math.max(height, top[4]);
            }
        }
        return height;
    }

    /** Where the cursor is on the level plane at a height: what is higher lies elsewhere under it. */
    static final class Pointer {
        private final Map<Float, Vector3f> places = new HashMap<>();

        /** Null where it points nowhere. */
        @Nullable
        Vector3f at(float height) {
            return places.get(height);
        }
    }

    Pointer pointer(Function<Float, Optional<Vector3f>> onPlane) {
        Pointer pointer = new Pointer();
        for (Path path : paths) {
            for (Vector3f point : path.points) {
                if (!pointer.places.containsKey(point.y)) {
                    pointer.places.put(point.y, onPlane.apply(point.y).orElse(null));
                }
            }
        }
        return pointer;
    }

    /** How the knife goes in. */
    enum Cut {
        /** Down between bread and mould, along x or along z. */
        SEAM_ALONG_X, SEAM_ALONG_Z,
        /** Into the bread from above, along x or along z. */
        LINE_ALONG_X, LINE_ALONG_Z,
        /** Lying flat, into the side. */
        LAYER
    }

    /** A place where to cut; on which side of the cake, if not from above. */
    record Place(Vector3f point, Cut cut, @Nullable Direction side) {
    }

    /** The place still to be cut that is nearest to the cursor, within {@code reach}; null for none. */
    @Nullable
    Place nearest(Pointer pointer, float reach) {
        Place best = null;
        float least = reach * reach;
        for (Path path : paths) {
            for (int i = 0; i < path.points.size(); i++) {
                Vector3f point = path.points.get(i);
                Vector3f at = path.isDone() ? null : pointer.at(point.y);
                float far = at == null ? Float.MAX_VALUE : Mth.square(point.x - at.x) + Mth.square(point.z - at.z);
                if (far < least) {
                    least = far;
                    boolean alongX = path.isAlongX(i);
                    best = new Place(point, path.side != null ? Cut.LAYER : path.line == 0 ? alongX ? Cut.SEAM_ALONG_X : Cut.SEAM_ALONG_Z
                            : alongX ? Cut.LINE_ALONG_X : Cut.LINE_ALONG_Z, path.side);
                }
            }
        }
        return best;
    }

    /** The knife went from one place to another. The paths that are done by that are added to {@code done}; whether it cut anything. */
    boolean cut(Pointer from, Pointer to, float way, List<Path> done) {
        int steps = Math.max(1, Mth.ceil(way / STEP));
        boolean any = false;
        for (Path path : paths) {
            if (path.isDone()) {
                continue;
            }
            for (int i = 1; i <= steps; i++) {
                any |= path.pass(from, to, (float) i / steps);
            }
            if (path.isDone()) {
                path.complete();
                done.add(path);
            }
        }
        return any;
    }

    void draw(PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        for (Path path : paths) {
            for (int i = 0; i < path.points.size(); i++) {
                if (path.cut[i]) {
                    mark(pose.last(), vertices, light, GROOVE, path, i, HALF_WIDTH, OVER);
                } else if (i % DASH_AND_GAP < DASH) {
                    mark(pose.last(), vertices, LightTexture.FULL_BRIGHT, OUTLINE, path, i, HALF_WIDTH + OUTLINE_WIDTH, OVER);
                    mark(pose.last(), vertices, LightTexture.FULL_BRIGHT, LINE, path, i, HALF_WIDTH, 1.5F * OVER);
                }
            }
        }
    }

    private static void mark(PoseStack.Pose pose, VertexConsumer vertices, int light, int color, Path path, int point, float halfWidth, float over) {
        Vector3f at = path.points.get(point);
        boolean alongX = path.isAlongX(point);
        float half = STEP / 2;
        if (path.side != null) {
            // Seen from above at a slant.
            halfWidth *= SIDE_WIDER;
            float x = at.x + over * path.side.getStepX();
            float z = at.z + over * path.side.getStepZ();
            float wideX = alongX ? half : 0;
            float wideZ = alongX ? 0 : half;
            corner(pose, vertices, light, color, x - wideX, at.y - halfWidth, z - wideZ);
            corner(pose, vertices, light, color, x - wideX, at.y + halfWidth, z - wideZ);
            corner(pose, vertices, light, color, x + wideX, at.y + halfWidth, z + wideZ);
            corner(pose, vertices, light, color, x + wideX, at.y - halfWidth, z + wideZ);
            return;
        }
        float x = alongX ? half : halfWidth;
        float z = alongX ? halfWidth : half;
        float y = at.y + over;
        corner(pose, vertices, light, color, at.x - x, y, at.z - z);
        corner(pose, vertices, light, color, at.x - x, y, at.z + z);
        corner(pose, vertices, light, color, at.x + x, y, at.z + z);
        corner(pose, vertices, light, color, at.x + x, y, at.z - z);
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer vertices, int light, int color, float x, float y, float z) {
        vertices.vertex(pose.pose(), x, y, z).color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >>> 24)
                .uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), 0, 1, 0).endVertex();
    }
}
