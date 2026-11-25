package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renyigesai.bakeries.block.fermentation_box.FermentationBoxBlockEntity;
import com.renyigesai.bakeries.block.oven.OvenBlockEntity;
import com.renyigesai.bakeries.block.toaster.ToasterBlockEntity;
import com.renyigesai.bakeries.init.BakeriesRecipeTypes;
import com.renyigesai.bakeries.recipe.oven.OvenRecipe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** What is baked, toasted or left to rise turns into what it will be, little by little: that is drawn over it, the clearer the further it is. */
public final class Ripening {
    private static final float CLOSER = 0.999F;
    /** When the product is all there. */
    private static final float SHOWN = 0.75F;
    /** What is all but gone is as good as gone, but shaders drop what has less. */
    private static final float LEAST = 0.02F;

    private Ripening() {
    }

    /** What lies in a slot becomes, and how far it is, 0 to 1. */
    public record Stage(ItemStack product, float done) {
        private static final Stage NONE = new Stage(ItemStack.EMPTY, 0);

        private static Stage of(ItemStack product, int time, int whole) {
            return product.isEmpty() || time <= 0 || whole <= 0 ? NONE : new Stage(product, Mth.clamp((float) time / whole, 0, 1));
        }

        public boolean isVisible() {
            return done > 0;
        }

        /** For the product. */
        public MultiBufferSource fade(MultiBufferSource buffers) {
            return done >= SHOWN ? buffers : Faded.of(buffers, done / SHOWN);
        }

        /** For what it is made of, which may stand out from under the product and goes once that is all there. */
        public MultiBufferSource fadeRaw(MultiBufferSource buffers) {
            return done <= SHOWN ? buffers : Faded.of(buffers, Math.max(LEAST, (1 - done) / (1 - SHOWN)));
        }
    }

    public static Stage inOven(OvenBlockEntity oven, int slot, int mostTemperature) {
        ItemStack product = product(oven.getLevel(), OvenRecipe.Type.INSTANCE, oven.getItem(slot));
        // Too hot, it only becomes charcoal.
        return Stage.of(!product.isEmpty() && mostTemperature > 0 && oven.temperature > mostTemperature ? new ItemStack(Items.CHARCOAL) : product,
                oven.cooking_times[slot], oven.max_cooking_times[slot]);
    }

    public static Stage inBox(FermentationBoxBlockEntity box, int slot) {
        return Stage.of(product(box.getLevel(), BakeriesRecipeTypes.FERMENTATION_BOX.get(), box.getItem(slot)), box.getFermentationTime()[slot],
                box.getFermentationMaxTime());
    }

    public static Stage inToaster(ToasterBlockEntity toaster, int slot) {
        return Stage.of(product(toaster.getLevel(), RecipeType.CAMPFIRE_COOKING, toaster.getItems().getStackInSlot(slot)),
                toaster.getCookingProgress()[slot], toaster.getCookingTime()[slot]);
    }

    /** Bakeries' oven recipes match on the server alone, but their ingredients are known here. */
    private static <T extends Recipe<?>> ItemStack product(Level level, RecipeType<T> type, ItemStack raw) {
        if (level == null || raw.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (Recipe<?> recipe : recipes(level, type)) {
            if (!recipe.getIngredients().isEmpty() && recipe.getIngredients().get(0).test(raw)) {
                return recipe.getResultItem(level.registryAccess());
            }
        }
        return ItemStack.EMPTY;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Iterable<Recipe<?>> recipes(Level level, RecipeType<?> type) {
        return (Iterable) level.getRecipeManager().getAllRecipesFor((RecipeType) type);
    }

    /** Towards the camera by a trace, which moves nothing on the screen: faces of the two models that nearly meet do not flicker. */
    public static void bringCloser(PoseStack pose) {
        pose.last().pose().scaleLocal(CLOSER);
    }
}
