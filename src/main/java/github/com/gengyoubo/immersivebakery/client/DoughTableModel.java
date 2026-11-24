package github.com.gengyoubo.immersivebakery.client;

import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The placed table without the rolling pin on its board: the scene has one that can be taken in the hand. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
final class DoughTableModel extends BakedModelWrapper<BakedModel> {
    /** The board's upper face; only the pin is higher. */
    private static final float BOARD = 17.01F / 16;

    private DoughTableModel(BakedModel whole) {
        super(whole);
    }

    @SubscribeEvent
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) -> location instanceof ModelResourceLocation variant
                && variant.getNamespace().equals("bakeries") && variant.getPath().equals("dough_crafting_table")
                && !variant.getVariant().equals("inventory") ? new DoughTableModel(model) : model);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return withoutPin(super.getQuads(state, side, random));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random, ModelData data,
                                    @Nullable RenderType type) {
        return withoutPin(super.getQuads(state, side, random, data, type));
    }

    private static List<BakedQuad> withoutPin(List<BakedQuad> quads) {
        return quads.stream().filter(quad -> !isPin(quad)).toList();
    }

    private static boolean isPin(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        for (int at = IQuadTransformer.POSITION; at < vertices.length; at += IQuadTransformer.STRIDE) {
            if (Float.intBitsToFloat(vertices[at + 1]) > BOARD) {
                return true;
            }
        }
        return false;
    }
}
