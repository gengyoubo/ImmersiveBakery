package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renyigesai.bakeries.block.stone_kiln.StoneKilnBlockEntity;
import com.renyigesai.bakeries.recipe.StoneKilnRecipe;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;

/** What bakes in the kiln turns into what it will be, little by little. */
public final class KilnBaking {
    private static final float CLOSER = 0.999F;

    private KilnBaking() {
    }

    /** Draws the baked good over the raw one Bakeries drew, the clearer the further it is. */
    public static void draw(StoneKilnBlockEntity kiln, float partialTick, PoseStack pose, MultiBufferSource buffers, int overlay) {
        Level level = kiln.getLevel();
        float baked = baked(kiln);
        ItemStack result = level == null || baked <= 0 ? ItemStack.EMPTY : result(level, kiln.getItem(0));
        if (result.isEmpty()) {
            return;
        }
        MultiBufferSource faded = type -> new Faded(buffers.getBuffer(Sheets.translucentCullBlockSheet()), baked);
        var minecraft = Minecraft.getInstance();
        float size = 0.5F + kiln.getSize();
        pose.pushPose();
        // Towards the camera by a trace, which moves nothing on the screen: faces of the two models that nearly meet do not flicker.
        pose.last().pose().scaleLocal(CLOSER);
        // Same transform as StoneKilnBlockEntityRender.render.
        pose.translate(0.5, 0.63125 + kiln.getSize() / 4, 0.5);
        float facing = kiln.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite().toYRot();
        pose.mulPose(Axis.YP.rotationDegrees(-facing - 180 + 180 * kiln.getProgress(partialTick)));
        pose.scale(size, size, size);
        if (result.getItem() instanceof BlockItem item) {
            minecraft.getItemRenderer().render(result, ItemDisplayContext.FIXED, false, pose, faded, LightTexture.FULL_BRIGHT, overlay,
                    minecraft.getBlockRenderer().getBlockModel(item.getBlock().defaultBlockState()));
        } else {
            minecraft.getItemRenderer().renderStatic(result, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, overlay, pose, faded, level, 0);
        }
        pose.popPose();
    }

    /** Draws whatever comes through it see-through. Items and blocks share the block atlas. */
    private static final class Faded extends VertexConsumerWrapper {
        private final float alpha;

        private Faded(VertexConsumer drawn, float alpha) {
            super(drawn);
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int opacity) {
            return super.color(red, green, blue, Math.round(opacity * alpha));
        }
    }

    /** How far all sides of it are baked together, 0 to 1. */
    private static float baked(StoneKilnBlockEntity kiln) {
        int all = kiln.getMaxCookingTime();
        return all <= 0 ? 0 : Mth.clamp((float) (kiln.getCookingTime() + kiln.getStageCookingTime()) / all, 0, 1);
    }

    private static ItemStack result(Level level, ItemStack raw) {
        var recipes = level.getRecipeManager();
        // Bakeries' StoneKilnRecipe.matches only runs on the server, but its ingredients are synced.
        return Stream.<Recipe<?>>concat(recipes.getAllRecipesFor(StoneKilnRecipe.Type.INSTANCE).stream(),
                        recipes.getAllRecipesFor(RecipeType.SMOKING).stream())
                .filter(recipe -> recipe.getIngredients().get(0).test(raw)).findFirst()
                .map(recipe -> recipe.getResultItem(level.registryAccess())).orElse(ItemStack.EMPTY);
    }
}
