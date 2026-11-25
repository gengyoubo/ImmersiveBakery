package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pockyl.diegesis.api.client.StationView;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;

/** A cupboard's own block model but for one side, which the scene has with drawers. */
final class CupboardPainter {
    private static final float EDGE = 0.001F;
    private static final Map<BakedModel, Map<Direction, BakedModel>> PARTS = new WeakHashMap<>();

    private CupboardPainter() {
    }

    static void draw(StationView view, Direction side, PoseStack pose, MultiBufferSource buffers) {
        BlockState cupboard = view.level().getBlockState(view.pos());
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(cupboard);
        BakedModel rest = PARTS.computeIfAbsent(model, whole -> new EnumMap<>(Direction.class)).computeIfAbsent(side, left -> new WithoutSide(model, left));
        TankPainter.draw(view, cupboard, rest, pose, buffers);
    }

    private static final class WithoutSide extends BakedModelWrapper<BakedModel> {
        private final Direction side;

        private WithoutSide(BakedModel whole, Direction side) {
            super(whole);
            this.side = side;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction of, RandomSource random, ModelData data,
                                        @Nullable RenderType type) {
            return super.getQuads(state, of, random, data, type).stream().filter(quad -> !isSide(quad)).toList();
        }

        /** All of its corners in that side of the block. */
        private boolean isSide(BakedQuad quad) {
            if (quad.getDirection() != side) {
                return false;
            }
            int axis = side.getAxis() == Direction.Axis.X ? 0 : 2;
            float at = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
            int[] vertices = quad.getVertices();
            for (int i = IQuadTransformer.POSITION; i < vertices.length; i += IQuadTransformer.STRIDE) {
                if (Math.abs(Float.intBitsToFloat(vertices[i + axis]) - at) > EDGE) {
                    return false;
                }
            }
            return true;
        }
    }
}
