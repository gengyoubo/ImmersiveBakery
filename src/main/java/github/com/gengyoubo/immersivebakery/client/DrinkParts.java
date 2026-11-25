package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pockyl.diegesis.api.client.StationView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;

/**
 * A finished drink's block model in its parts: the cup, what is poured into it, and what falls into that: ice, cream, a straw. The
 * model does not name them, but what hangs together in it is a part, and each looks different.
 */
final class DrinkParts {
    /** Pixels. The drink is a box of this size; only long things that go higher are straws. */
    private static final float DRINK_LOW = 1.25F;
    private static final float DRINK_HIGH = 9.25F;
    private static final float DRINK_WIDE = 4;
    private static final float NEAR = 0.1F;
    private static final float STRAW = 12;
    private static final float STRAW_LONG = 6;
    /** What lies on the drink begins and ends higher than that. */
    private static final float ON_TOP_LOW = 8.9F;
    private static final float ON_TOP_HIGH = 9.6F;
    private static final float EMPTY = 0.3F;
    private static final float ALIGNED = 0.99F;
    private static final Map<BakedModel, DrinkParts> PARTS = new WeakHashMap<>();

    enum Kind { ICE, CREAM, STRAW }

    record Piece(Kind kind, List<BakedQuad> quads, float low) {
    }

    private final List<BakedQuad> cup = new ArrayList<>();
    private final List<BakedQuad> drink = new ArrayList<>();
    private final List<Piece> pieces = new ArrayList<>();
    private final boolean translucent;
    private Integer color;

    private DrinkParts(BakedModel model, BlockState state) {
        RandomSource random = RandomSource.create(0);
        translucent = model.getRenderTypes(state, random, ModelData.EMPTY).contains(RenderType.translucent());
        List<BakedQuad> quads = new ArrayList<>();
        for (int side = 0; side <= 6; side++) {
            quads.addAll(model.getQuads(state, side == 6 ? null : Direction.from3DDataValue(side), random, ModelData.EMPTY, null));
        }
        for (List<BakedQuad> part : connected(quads)) {
            sort(part);
        }
        pieces.sort(Comparator.comparing((Piece piece) -> piece.kind == Kind.STRAW).thenComparing(Piece::low));
    }

    /** Of the block this item places; null when it places none. */
    static DrinkParts of(ItemStack drink) {
        if (!(drink.getItem() instanceof BlockItem item)) {
            return null;
        }
        BlockState state = item.getBlock().defaultBlockState();
        return PARTS.computeIfAbsent(Minecraft.getInstance().getBlockRenderer().getBlockModel(state), model -> new DrinkParts(model, state));
    }

    /** Of what is poured in, as its sides are painted; {@code otherwise} where that says nothing. */
    int color(int otherwise) {
        if (color == null) {
            color = sample();
        }
        return color < 0 ? otherwise : color;
    }

    private int sample() {
        long[] sum = new long[3];
        int count = 0;
        for (BakedQuad quad : drink) {
            if (quad.getDirection().getAxis().isVertical()) {
                continue;
            }
            TextureAtlasSprite sprite = quad.getSprite();
            int[] vertices = quad.getVertices();
            float lowU = Float.MAX_VALUE;
            float highU = -Float.MAX_VALUE;
            float lowV = Float.MAX_VALUE;
            float highV = -Float.MAX_VALUE;
            for (int at = IQuadTransformer.UV0; at < vertices.length; at += IQuadTransformer.STRIDE) {
                float u = sprite.getUOffset(Float.intBitsToFloat(vertices[at])) / 16 * sprite.contents().width();
                float v = sprite.getVOffset(Float.intBitsToFloat(vertices[at + 1])) / 16 * sprite.contents().height();
                lowU = Math.min(lowU, u);
                highU = Math.max(highU, u);
                lowV = Math.min(lowV, v);
                highV = Math.max(highV, v);
            }
            for (int x = Math.round(lowU); x < Math.round(highU); x++) {
                for (int y = Math.round(lowV); y < Math.round(highV); y++) {
                    int pixel = sprite.getPixelRGBA(0, Mth.clamp(x, 0, sprite.contents().width() - 1), Mth.clamp(y, 0, sprite.contents().height() - 1));
                    if (pixel >>> 24 >= 128) {
                        sum[0] += pixel & 0xFF;
                        sum[1] += pixel >> 8 & 0xFF;
                        sum[2] += pixel >> 16 & 0xFF;
                        count++;
                    }
                }
            }
        }
        return count == 0 ? -1 : (int) (sum[0] / count) << 16 | (int) (sum[1] / count) << 8 | (int) (sum[2] / count);
    }

    /** What falls in, in the order it does. */
    List<Piece> pieces() {
        return pieces;
    }

    static float surface(float filled) {
        return DRINK_LOW + (DRINK_HIGH - DRINK_LOW) * filled;
    }

    /** Quads that share a corner, directly or through others. */
    private static List<List<BakedQuad>> connected(List<BakedQuad> quads) {
        int[] parent = new int[quads.size()];
        Map<String, Integer> corners = new HashMap<>();
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
            for (Vector3f corner : corners(quads.get(i))) {
                String key = Math.round(corner.x * 50) + "," + Math.round(corner.y * 50) + "," + Math.round(corner.z * 50);
                Integer other = corners.putIfAbsent(key, i);
                if (other != null) {
                    parent[root(parent, i)] = root(parent, other);
                }
            }
        }
        Map<Integer, List<BakedQuad>> parts = new HashMap<>();
        for (int i = 0; i < parent.length; i++) {
            parts.computeIfAbsent(root(parent, i), key -> new ArrayList<>()).add(quads.get(i));
        }
        return new ArrayList<>(parts.values());
    }

    private static int root(int[] parent, int at) {
        while (parent[at] != at) {
            at = parent[at];
        }
        return at;
    }

    /** Pixels. */
    private static Vector3f[] corners(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        Vector3f[] corners = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            int at = IQuadTransformer.POSITION + i * IQuadTransformer.STRIDE;
            corners[i] = new Vector3f(Float.intBitsToFloat(vertices[at]), Float.intBitsToFloat(vertices[at + 1]), Float.intBitsToFloat(vertices[at + 2])).mul(16);
        }
        return corners;
    }

    private void sort(List<BakedQuad> part) {
        Vector3f low = new Vector3f(Float.MAX_VALUE);
        Vector3f high = new Vector3f(-Float.MAX_VALUE);
        boolean aligned = true;
        for (BakedQuad quad : part) {
            Vector3f[] corners = corners(quad);
            for (Vector3f corner : corners) {
                low.min(corner);
                high.max(corner);
            }
            Vector3f normal = new Vector3f(corners[2]).sub(corners[0]).cross(new Vector3f(corners[3]).sub(corners[1]));
            if (normal.length() > 0.01F) {
                normal.normalize().absolute();
                aligned &= Math.max(normal.x, Math.max(normal.y, normal.z)) > ALIGNED;
            }
        }
        if (high.y > STRAW && high.y - low.y > STRAW_LONG) {
            pieces.add(new Piece(Kind.STRAW, part, low.y));
        } else if (!aligned || low.y > ON_TOP_LOW && high.y > ON_TOP_HIGH) {
            pieces.add(new Piece(low.y > ON_TOP_LOW ? Kind.CREAM : Kind.ICE, part, low.y));
        } else if (Math.abs(low.y - DRINK_LOW) < NEAR && Math.abs(high.y - DRINK_HIGH) < NEAR && Math.abs(high.x - low.x - DRINK_WIDE) < NEAR) {
            drink.addAll(part);
        } else {
            cup.addAll(part);
        }
    }

    /** Puts the pose, in pixels from the middle of the bottom, into the block, which is not turned to the player as the scene is. */
    private static void enterBlock(StationView view, PoseStack pose) {
        pose.mulPose(Axis.YP.rotationDegrees(view.facing().toYRot() - 180));
        pose.scale(16, 16, 16);
        pose.translate(-0.5F, 0, -0.5F);
    }

    /** {@code filled} from 0 to 1. */
    void drawCup(StationView view, PoseStack pose, MultiBufferSource buffers, int light, float filled) {
        pose.pushPose();
        enterBlock(view, pose);
        if (filled > 0.01F && !drink.isEmpty()) {
            pose.pushPose();
            pose.translate(0, DRINK_LOW / 16, 0);
            pose.scale(1, filled, 1);
            pose.translate(0, -DRINK_LOW / 16, 0);
            draw(drink, pose, buffers, light, 1);
            pose.popPose();
        }
        // Empty, it is seen through.
        draw(cup, pose, buffers, light, Mth.lerp(filled * filled, EMPTY, 1));
        pose.popPose();
    }

    /** That many pixels over where it belongs. */
    void drawPiece(StationView view, Piece piece, PoseStack pose, MultiBufferSource buffers, int light, float over) {
        pose.pushPose();
        pose.translate(0, over, 0);
        enterBlock(view, pose);
        draw(piece.quads, pose, buffers, light, 1);
        pose.popPose();
    }

    private void draw(List<BakedQuad> quads, PoseStack pose, MultiBufferSource buffers, int light, float alpha) {
        VertexConsumer vertices = buffers.getBuffer(translucent || alpha < 1 ? Sheets.translucentCullBlockSheet() : Sheets.cutoutBlockSheet());
        for (BakedQuad quad : quads) {
            vertices.putBulkData(pose.last(), quad, 1, 1, 1, alpha, light, OverlayTexture.NO_OVERLAY, false);
        }
    }
}
