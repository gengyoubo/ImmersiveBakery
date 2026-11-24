package github.com.gengyoubo.immersivebakery.cup;

import com.renyigesai.bakeries.block.glass_drink_cup.GlassDrinkCupBlockEntity;
import com.renyigesai.bakeries.recipe.CoffeeRecipe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/**
 * What is in Bakeries' drink cup and what that makes. Bakeries wants it in the recipe's order and nothing looks at what is in there
 * besides; here the order does not matter, and only what some recipe has a use for goes in.
 */
public final class CupContents {
    public static final int PLACES = 4;
    /** Where Bakeries keeps the drink that the rest makes. */
    private static final int DRINK = 4;

    private CupContents() {
    }

    public static List<ItemStack> in(GlassDrinkCupBlockEntity cup) {
        List<ItemStack> in = new ArrayList<>();
        for (int slot = 0; slot < PLACES; slot++) {
            ItemStack stack = cup.getInventory().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                in.add(stack);
            }
        }
        return in;
    }

    public static List<CoffeeRecipe> recipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(CoffeeRecipe.Type.INSTANCE).stream()
                .filter(recipe -> recipe.getIngredients().size() <= PLACES).toList();
    }

    /** What all of this makes, no more and no less; null when it is nothing. */
    @Nullable
    public static CoffeeRecipe made(Level level, List<ItemStack> in) {
        return recipes(level).stream().filter(recipe -> recipe.getIngredients().size() == in.size() && places(recipe, in) != null)
                .findFirst().orElse(null);
    }

    /** Whether some recipe goes on from what is in with this. */
    public static boolean accepts(Level level, List<ItemStack> in, ItemStack stack) {
        List<ItemStack> then = new ArrayList<>(in);
        then.add(stack);
        return !stack.isEmpty() && recipes(level).stream().anyMatch(recipe -> places(recipe, then) != null);
    }

    /** The ingredient each of them is, all different ones; null when one of them is none. */
    @Nullable
    private static int[] places(CoffeeRecipe recipe, List<ItemStack> in) {
        int[] places = new int[in.size()];
        return in.size() <= recipe.getIngredients().size() && place(recipe, in, 0, places) ? places : null;
    }

    private static boolean place(CoffeeRecipe recipe, List<ItemStack> in, int at, int[] places) {
        if (at == in.size()) {
            return true;
        }
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            int ingredient = i;
            if (Arrays.stream(places, 0, at).noneMatch(taken -> taken == ingredient) && recipe.getIngredients().get(i).test(in.get(at))) {
                places[at] = i;
                if (place(recipe, in, at + 1, places)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** What is poured in leaves its vessel with the player and cannot be taken out again. */
    public static boolean isPoured(ItemStack stack) {
        return stack.hasCraftingRemainingItem();
    }

    /** Puts it back in the order of the recipe it makes, if any, as Bakeries needs it to see that. */
    static void set(GlassDrinkCupBlockEntity cup, List<ItemStack> in) {
        Level level = cup.getLevel();
        CoffeeRecipe made = made(level, in);
        int[] places = made == null ? null : places(made, in);
        ItemStack[] slots = new ItemStack[PLACES];
        Arrays.fill(slots, ItemStack.EMPTY);
        for (int i = 0; i < in.size(); i++) {
            slots[places == null ? i : places[i]] = in.get(i);
        }
        for (int slot = 0; slot < PLACES; slot++) {
            cup.getInventory().setStackInSlot(slot, slots[slot].copy());
        }
        cup.getInventory().setStackInSlot(DRINK, made == null ? ItemStack.EMPTY : made.getResultItem(level.registryAccess()).copy());
        cup.setChanged();
        level.sendBlockUpdated(cup.getBlockPos(), cup.getBlockState(), cup.getBlockState(), 3);
    }

    /** The slots to take what a recipe still lacks from, one item each; null when not all of it is there, or something else is in. */
    @Nullable
    public static List<Integer> missingFrom(CoffeeRecipe recipe, List<ItemStack> in, Container inventory) {
        int[] places = places(recipe, in);
        if (places == null) {
            return null;
        }
        int[] left = new int[inventory.getContainerSize()];
        Arrays.setAll(left, slot -> inventory.getItem(slot).getCount());
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            int place = i;
            if (Arrays.stream(places).anyMatch(taken -> taken == place)) {
                continue;
            }
            Ingredient ingredient = recipe.getIngredients().get(i);
            int from = -1;
            for (int slot = 0; slot < left.length && from < 0; slot++) {
                from = left[slot] > 0 && ingredient.test(inventory.getItem(slot)) ? slot : -1;
            }
            if (from < 0) {
                return null;
            }
            left[from]--;
            slots.add(from);
        }
        return slots;
    }
}
