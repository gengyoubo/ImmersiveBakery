package github.com.gengyoubo.immersivebakery.dough;

import com.renyigesai.bakeries.recipe.DoughCraftingRecipe;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** What a dough can be shaped into, by Bakeries' own recipes and in its order. */
public final class DoughShaping {
    /** Products that are rolled out before they are shaped; all others are shaped from the lump. */
    public static final TagKey<Item> ROLLED_FLAT = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, "rolled_flat"));

    private DoughShaping() {
    }

    public static List<DoughCraftingRecipe> recipes(Level level, ItemStack dough) {
        return dough.isEmpty() ? List.of()
                : level.getRecipeManager().getRecipesFor(DoughCraftingRecipe.Type.INSTANCE, new SimpleContainer(dough), level);
    }

    @Nullable
    public static DoughCraftingRecipe recipe(Level level, ItemStack dough, @Nullable ResourceLocation id) {
        return recipes(level, dough).stream().filter(recipe -> recipe.getId().equals(id)).findFirst().orElse(null);
    }

    /** What one piece of the dough gives; perfectly fermented dough gives more. */
    public static ItemStack product(Level level, ItemStack dough, DoughCraftingRecipe recipe) {
        ItemStack product = recipe.assemble(new SimpleContainer(dough.copy()), level.registryAccess());
        return product.isItemEnabled(level.enabledFeatures()) ? product : ItemStack.EMPTY;
    }

    /** Takes as many pieces of the dough as the products of fit in that many slots, and gives those products. */
    public static List<ItemStack> shape(Level level, ItemStack dough, DoughCraftingRecipe recipe, int slots) {
        ItemStack product = product(level, dough, recipe);
        if (product.isEmpty()) {
            return List.of();
        }
        int pieces = Math.min(dough.getCount(), slots * product.getMaxStackSize() / product.getCount());
        List<ItemStack> products = new ArrayList<>();
        for (int left = pieces * product.getCount(); left > 0; left -= product.getMaxStackSize()) {
            products.add(product.copyWithCount(Math.min(left, product.getMaxStackSize())));
        }
        dough.shrink(pieces);
        return products;
    }
}
