package github.com.gengyoubo.immersivebakery.blender;

import com.renyigesai.bakeries.block.blender.BlenderBlock;
import com.renyigesai.bakeries.block.blender.BlenderBlockEntity;
import com.renyigesai.bakeries.recipe.BlenderRecipe;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;

public final class BlenderPower {
    private static final String POWER = ImmersiveBakery.MOD_ID + ":blender_power";
    private static final String RECIPE = ImmersiveBakery.MOD_ID + ":blender_recipe";

    private BlenderPower() {
    }

    static void useManualPower(BlenderBlockEntity blender) {
        BlenderRecipe recipe = BlenderRecipes.find(blender);
        if (!isManual(blender)) {
            blender.getPersistentData().putString(RECIPE, idOf(recipe));
            setPowered(blender, recipe != null && blender.cookingTotalTime > 0);
        }
        forgetOtherRecipe(blender, recipe);
    }

    public static boolean isPowered(BlenderBlockEntity blender) {
        return blender.getPersistentData().getBoolean(POWER);
    }

    /** Bakeries' own screen would hand out the liquids in the bowl together with vessels that are back with the player already. */
    public static boolean allowsNativeMenu(BlenderBlockEntity blender) {
        return !isPowered(blender) && !BlenderLiquids.hasStored(blender);
    }

    private static boolean isManual(BlenderBlockEntity blender) {
        return blender.getPersistentData().contains(POWER);
    }

    static void toggle(BlenderBlockEntity blender) {
        if (isPowered(blender)) {
            setPowered(blender, false);
            return;
        }
        BlenderRecipe recipe = BlenderRecipes.find(blender);
        forgetOtherRecipe(blender, recipe);
        if (recipe != null && hasRoom(blender, recipe) && !awaitsContainer(blender, recipe)) {
            setPowered(blender, true);
        }
    }

    /** Whether Bakeries' tick may run now. */
    public static boolean beforeTick(BlenderBlockEntity blender) {
        if (!isManual(blender)) {
            return true;
        }
        if (!isPowered(blender)) {
            return false;
        }
        BlenderRecipe recipe = BlenderRecipes.find(blender);
        forgetOtherRecipe(blender, recipe);
        if (recipe != null && awaitsContainer(blender, recipe)) {
            setPowered(blender, false);
            return false;
        }
        return true;
    }

    public static void stopAfterBatch(BlenderBlockEntity blender) {
        if (isPowered(blender)) {
            BlenderRecipe recipe = BlenderRecipes.find(blender);
            if (recipe == null || !hasRoom(blender, recipe)) {
                setPowered(blender, false);
            }
        }
    }

    /** A hand-run blender mixes first; the container is only needed to empty the bowl. */
    public static boolean mixesWithoutContainer(BlenderBlockEntity blender) {
        return isManual(blender) && blender.cookingTotalTime < BlenderStation.MIX_TICKS;
    }

    static boolean isMixed(BlenderBlockEntity blender) {
        if (blender.cookingTotalTime < BlenderStation.MIX_TICKS) {
            return false;
        }
        BlenderRecipe recipe = BlenderRecipes.find(blender);
        return recipe != null && awaitsContainer(blender, recipe);
    }

    /** Fills one {@code container} with the mixed batter through Bakeries' crafting; empty if that is not possible. */
    static ItemStack scoop(BlenderBlockEntity blender, ItemStack container) {
        BlenderRecipe recipe = BlenderRecipes.find(blender);
        forgetOtherRecipe(blender, recipe);
        if (container.isEmpty() || recipe == null || isPowered(blender) || !awaitsContainer(blender, recipe)
                || !recipe.getContainer().is(container.getItem())) {
            return ItemStack.EMPTY;
        }
        var inventory = blender.getInventory();
        ItemStack placed = inventory.getStackInSlot(BlenderStation.CONTAINER_SLOT);
        ItemStack waiting = inventory.getStackInSlot(BlenderStation.OUTPUT_SLOT);
        inventory.setStackInSlot(BlenderStation.CONTAINER_SLOT, container.copyWithCount(1));
        inventory.setStackInSlot(BlenderStation.OUTPUT_SLOT, ItemStack.EMPTY);
        ItemStack filled = ItemStack.EMPTY;
        if (((BlenderCrafting) blender).immersiveBakery$craft()) {
            filled = inventory.getStackInSlot(BlenderStation.OUTPUT_SLOT);
        }
        inventory.setStackInSlot(BlenderStation.CONTAINER_SLOT, placed);
        inventory.setStackInSlot(BlenderStation.OUTPUT_SLOT, waiting);
        sync(blender);
        return filled;
    }

    static void sync(BlenderBlockEntity blender) {
        blender.setChanged();
        var state = blender.getBlockState();
        blender.getLevel().sendBlockUpdated(blender.getBlockPos(), state, state, 3);
    }

    private static boolean awaitsContainer(BlenderBlockEntity blender, BlenderRecipe recipe) {
        ItemStack container = recipe.getContainer();
        return blender.cookingTotalTime >= BlenderStation.MIX_TICKS && !container.isEmpty()
                && !container.is(blender.getInventory().getStackInSlot(BlenderStation.CONTAINER_SLOT).getItem());
    }

    private static boolean hasRoom(BlenderBlockEntity blender, BlenderRecipe recipe) {
        ItemStack result = recipe.getResultItem(null);
        ItemStack waiting = blender.getInventory().getStackInSlot(BlenderStation.OUTPUT_SLOT);
        return waiting.isEmpty() || result.is(waiting.getItem())
                && waiting.getCount() + result.getCount() <= waiting.getMaxStackSize();
    }

    /** Bakeries keeps the mixing time when the ingredients are exchanged for those of another recipe. */
    private static void forgetOtherRecipe(BlenderBlockEntity blender, @Nullable BlenderRecipe recipe) {
        String id = idOf(recipe);
        if (!blender.getPersistentData().getString(RECIPE).equals(id)) {
            blender.getPersistentData().putString(RECIPE, id);
            blender.cookingTotalTime = 0;
            blender.setChanged();
        }
    }

    private static String idOf(@Nullable BlenderRecipe recipe) {
        return recipe == null ? "" : recipe.getId().toString();
    }

    private static void setPowered(BlenderBlockEntity blender, boolean powered) {
        blender.getPersistentData().putBoolean(POWER, powered);
        var state = blender.getBlockState();
        if (state.getValue(BlenderBlock.POWERED) != powered) {
            blender.getLevel().setBlockAndUpdate(blender.getBlockPos(), state.setValue(BlenderBlock.POWERED, powered));
        }
        sync(blender);
    }
}
