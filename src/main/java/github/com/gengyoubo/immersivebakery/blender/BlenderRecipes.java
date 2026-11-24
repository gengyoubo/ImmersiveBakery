package github.com.gengyoubo.immersivebakery.blender;

import com.renyigesai.bakeries.block.blender.BlenderBlockEntity;
import com.renyigesai.bakeries.recipe.BlenderRecipe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;

public final class BlenderRecipes {
    private BlenderRecipes() {
    }

    @Nullable
    static BlenderRecipe find(BlenderBlockEntity blender) {
        return find(blender.getLevel(), IntStream.range(0, BlenderStation.INGREDIENT_SLOTS)
                .mapToObj(blender.getInventory()::getStackInSlot).toList());
    }

    @Nullable
    public static BlenderRecipe find(Level level, List<ItemStack> ingredients) {
        SimpleContainer container = new SimpleContainer(BlenderStation.INGREDIENT_SLOTS);
        int slot = 0;
        for (ItemStack stack : ingredients) {
            if (!stack.isEmpty()) {
                container.setItem(slot++, stack);
            }
        }
        return slot == 0 ? null : level.getRecipeManager().getRecipeFor(BlenderRecipe.Type.INSTANCE, container, level).orElse(null);
    }

    public static boolean isIngredient(Level level, ItemStack stack) {
        return !stack.isEmpty() && all(level).stream()
                .anyMatch(recipe -> recipe.getIngredients().stream().anyMatch(ingredient -> takes(ingredient, stack)));
    }

    public static boolean isContainer(Level level, ItemStack stack) {
        return !stack.isEmpty() && all(level).stream().anyMatch(recipe -> recipe.getContainer().is(stack.getItem()));
    }

    /** Whether a result comes in a container of its own instead of lying in the bowl. */
    public static boolean isServedInContainer(Level level, ItemStack result) {
        return all(level).stream()
                .anyMatch(recipe -> !recipe.getContainer().isEmpty() && recipe.getResultItem(null).is(result.getItem()));
    }

    /** Whether a stack, poured in if it is a liquid, is an ingredient. */
    private static boolean takes(Ingredient ingredient, ItemStack stack) {
        return ingredient.test(BlenderLiquids.isLiquid(stack) ? BlenderLiquids.store(stack) : stack);
    }

    /**
     * Bakeries wants every ingredient of a recipe in a slot of its own: shares what lies in the bowl out over as many slots as
     * the recipe takes. Returns what those slots cannot hold. Ingredients that make nothing stay where they are.
     */
    static List<ItemStack> shareOut(BlenderBlockEntity blender) {
        List<ItemStack> kinds = kinds(blender);
        if (kinds.isEmpty()) {
            return List.of();
        }
        BlenderRecipe current = find(blender);
        List<BlenderRecipe> recipes = new ArrayList<>(all(blender.getLevel()));
        recipes.sort(Comparator.comparing((BlenderRecipe recipe) -> recipe != current)
                .thenComparing(recipe -> -recipe.getIngredients().size())
                .thenComparing(BlenderRecipe::getId));
        for (BlenderRecipe recipe : recipes) {
            int[] parts = parts(recipe.getIngredients(), kinds);
            if (parts != null) {
                return lay(blender.getInventory(), kinds, parts);
            }
        }
        return List.of();
    }

    /**
     * Takes up to {@code sets} whole sets of a recipe from the inventory into the bowl, as many as its slots hold. A bowl that holds
     * something the recipe does not use takes nothing. Items alike to what lies in the bowl come first, and liquid left over from
     * the sets in it is used up before more is poured. {@code poured} gets the full vessels that were emptied, one a stack.
     */
    static boolean fill(BlenderBlockEntity blender, Inventory inventory, BlenderRecipe recipe, int sets, List<ItemStack> poured) {
        List<Ingredient> ingredients = recipe.getIngredients();
        List<ItemStack> kinds = kinds(blender);
        if (kinds.stream().anyMatch(kind -> ingredients.stream().noneMatch(ingredient -> ingredient.test(kind)))) {
            return false;
        }
        int[] parts = null;
        for (int done = 0; done < sets; done++) {
            List<ItemStack> more = new ArrayList<>(kinds.stream().map(ItemStack::copy).toList());
            int[] spare = spareLiquid(ingredients, kinds);
            int[] from = new int[ingredients.size()];
            boolean found = true;
            for (int i = 0; i < from.length && found; i++) {
                Ingredient ingredient = ingredients.get(i);
                List<ItemStack> held = kinds;
                int left = IntStream.range(0, spare.length).filter(kind -> spare[kind] > 0 && ingredient.test(held.get(kind)))
                        .findFirst().orElse(-1);
                if (left >= 0) {
                    spare[left]--;
                    from[i] = -1;
                    continue;
                }
                from[i] = source(inventory, ingredient, kinds, from, i);
                found = from[i] >= 0;
                if (found) {
                    ItemStack one = inventory.getItem(from[i]).copyWithCount(1);
                    add(more, BlenderLiquids.isLiquid(one) ? BlenderLiquids.store(one) : one);
                }
            }
            int[] laid = found ? parts(ingredients, more) : null;
            if (laid == null || !fits(more, laid)) {
                break;
            }
            for (int slot : from) {
                if (slot >= 0) {
                    ItemStack taken = inventory.removeItem(slot, 1);
                    if (BlenderLiquids.isLiquid(taken)) {
                        poured.add(taken);
                    }
                }
            }
            kinds = more;
            parts = laid;
        }
        if (parts != null) {
            lay(blender.getInventory(), kinds, parts);
        }
        return parts != null;
    }

    /** How much of each liquid in the bowl the sets it holds do not use. */
    private static int[] spareLiquid(List<Ingredient> ingredients, List<ItemStack> kinds) {
        int[] parts = kinds.isEmpty() ? null : parts(ingredients, kinds);
        int sets = parts == null ? 0 : sets(kinds, parts);
        return IntStream.range(0, kinds.size()).map(kind -> !BlenderLiquids.isStored(kinds.get(kind)) ? 0
                : kinds.get(kind).getCount() - (parts == null ? 0 : sets * parts[kind])).toArray();
    }

    /** The inventory slot that gives the next item for an ingredient and is not taken up by earlier ones of the set, or -1. */
    private static int source(Inventory inventory, Ingredient ingredient, List<ItemStack> held, int[] taken, int index) {
        int any = -1;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            int wanted = slot;
            long used = IntStream.range(0, index).filter(earlier -> taken[earlier] == wanted).count();
            if (stack.isEmpty() || !takes(ingredient, stack) || stack.getCount() <= used) {
                continue;
            }
            if (held.stream().anyMatch(kind -> ItemStack.isSameItemSameTags(kind, stack))) {
                return slot;
            }
            if (any < 0) {
                any = slot;
            }
        }
        return any;
    }

    private static int hold(ItemStack kind) {
        return BlenderLiquids.isStored(kind) ? BlenderLiquids.MOST : kind.getMaxStackSize();
    }

    private static boolean fits(List<ItemStack> kinds, int[] parts) {
        return IntStream.range(0, parts.length).allMatch(kind -> kinds.get(kind).getCount() <= parts[kind] * hold(kinds.get(kind)));
    }

    /** What lies in the bowl, each kind in one stack of any size. */
    private static List<ItemStack> kinds(BlenderBlockEntity blender) {
        List<ItemStack> kinds = new ArrayList<>();
        for (int slot = 0; slot < BlenderStation.INGREDIENT_SLOTS; slot++) {
            ItemStack stack = blender.getInventory().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                add(kinds, stack.copy());
            }
        }
        return kinds;
    }

    private static void add(List<ItemStack> kinds, ItemStack stack) {
        kinds.stream().filter(kind -> ItemStack.isSameItemSameTags(kind, stack)).findFirst()
                .ifPresentOrElse(kind -> kind.grow(stack.getCount()), () -> kinds.add(stack));
    }

    /** How many of the ingredients each kind stands for when every kind is used, the way that makes most sets; or null. */
    @Nullable
    private static int[] parts(List<Ingredient> ingredients, List<ItemStack> kinds) {
        int[][] best = new int[1][];
        assign(ingredients, kinds, 0, new int[kinds.size()], best);
        return best[0];
    }

    private static void assign(List<Ingredient> ingredients, List<ItemStack> kinds, int index, int[] parts, int[][] best) {
        long unused = IntStream.of(parts).filter(part -> part == 0).count();
        if (unused > ingredients.size() - index) {
            return;
        }
        if (index == ingredients.size()) {
            if (best[0] == null || sets(kinds, parts) > sets(kinds, best[0])) {
                best[0] = parts.clone();
            }
            return;
        }
        for (int kind = 0; kind < parts.length; kind++) {
            if (parts[kind] < kinds.get(kind).getCount() && ingredients.get(index).test(kinds.get(kind))) {
                parts[kind]++;
                assign(ingredients, kinds, index + 1, parts, best);
                parts[kind]--;
            }
        }
    }

    private static int sets(List<ItemStack> kinds, int[] parts) {
        return IntStream.range(0, parts.length).map(kind -> kinds.get(kind).getCount() / parts[kind]).min().orElse(0);
    }

    private static List<ItemStack> lay(ItemStackHandler inventory, List<ItemStack> kinds, int[] parts) {
        ItemStack[] laid = new ItemStack[BlenderStation.INGREDIENT_SLOTS];
        int[] missing = parts.clone();
        int[] left = kinds.stream().mapToInt(ItemStack::getCount).toArray();
        // First pass: a kind keeps the slots it lies in. Second pass: it spreads to free ones.
        for (int pass = 0; pass < 2; pass++) {
            for (int kind = 0; kind < parts.length; kind++) {
                ItemStack stack = kinds.get(kind);
                for (int slot = 0; slot < laid.length && missing[kind] > 0; slot++) {
                    if (laid[slot] != null || pass == 0 && !ItemStack.isSameItemSameTags(inventory.getStackInSlot(slot), stack)) {
                        continue;
                    }
                    int count = Math.min(hold(stack), (left[kind] + missing[kind] - 1) / missing[kind]);
                    laid[slot] = stack.copyWithCount(count);
                    left[kind] -= count;
                    missing[kind]--;
                }
            }
        }
        for (int slot = 0; slot < laid.length; slot++) {
            ItemStack stack = laid[slot] == null ? ItemStack.EMPTY : laid[slot];
            if (!ItemStack.matches(inventory.getStackInSlot(slot), stack)) {
                inventory.setStackInSlot(slot, stack);
            }
        }
        List<ItemStack> rest = new ArrayList<>();
        for (int kind = 0; kind < parts.length; kind++) {
            int most = hold(kinds.get(kind));
            for (int count = left[kind]; count > 0; count -= most) {
                rest.add(kinds.get(kind).copyWithCount(Math.min(count, most)));
            }
        }
        return rest;
    }

    public static List<BlenderRecipe> all(Level level) {
        return level.getRecipeManager().getAllRecipesFor(BlenderRecipe.Type.INSTANCE);
    }
}
