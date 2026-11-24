package github.com.gengyoubo.immersivebakery.blender;

import com.renyigesai.bakeries.block.blender.BlenderBlockEntity;
import com.renyigesai.bakeries.init.BakeriesItems;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;

/**
 * Liquids poured into the bowl. Their vessels are given back at once; what stays in the ingredient slots is the filled item,
 * which is what Bakeries' recipes match, marked as liquid without a vessel: one item for each bottle of it. A marked stack never
 * leaves the bowl as an item.
 */
public final class BlenderLiquids {
    /** How many bottles a bucket holds, as Bakeries bottles milk. */
    public static final int BOTTLES_PER_BUCKET = 3;
    /** The most bottles of a liquid one slot holds. */
    static final int MOST = 64;
    private static final String STORED = ImmersiveBakery.MOD_ID + ":liquid";

    private BlenderLiquids() {
    }

    /** Whether an item is emptied out of its bottle or bucket into the bowl. */
    public static boolean isLiquid(ItemStack stack) {
        return vessel(stack) != Items.AIR;
    }

    public static boolean isStored(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(STORED);
    }

    static boolean hasStored(BlenderBlockEntity blender) {
        for (int slot = 0; slot < BlenderStation.INGREDIENT_SLOTS; slot++) {
            if (isStored(blender.getInventory().getStackInSlot(slot))) {
                return true;
            }
        }
        return false;
    }

    /** What the bowl holds once {@code full} is poured into it. */
    public static ItemStack store(ItemStack full) {
        ItemStack stored = full.copy();
        if (full.is(Items.MILK_BUCKET)) {
            stored = new ItemStack(BakeriesItems.BOTTLE_MILK.get(), full.getCount() * BOTTLES_PER_BUCKET);
        } else if (full.is(Items.WATER_BUCKET)) {
            stored.setCount(full.getCount() * BOTTLES_PER_BUCKET);
        }
        stored.getOrCreateTag().putBoolean(STORED, true);
        return stored;
    }

    /** Whether one more of a vessel can be poured in; what is no liquid is none of its business. */
    static boolean hasRoom(BlenderBlockEntity blender, ItemStack full) {
        if (!isLiquid(full)) {
            return true;
        }
        ItemStack stored = store(full);
        int there = 0;
        for (int slot = 0; slot < BlenderStation.INGREDIENT_SLOTS; slot++) {
            ItemStack stack = blender.getInventory().getStackInSlot(slot);
            there += ItemStack.isSameItemSameTags(stack, stored) ? stack.getCount() : 0;
        }
        return there + stored.getCount() <= MOST;
    }

    static ItemStack emptied(ItemStack full) {
        return new ItemStack(vessel(full), full.getCount());
    }

    /** Draws as much liquid from the bowl as fills one {@code vessel}, the last poured in first; empty if none goes into it. */
    static ItemStack draw(BlenderBlockEntity blender, ItemStack vessel) {
        for (int slot = BlenderStation.INGREDIENT_SLOTS - 1; slot >= 0; slot--) {
            ItemStack stored = blender.getInventory().getStackInSlot(slot);
            ItemStack filled = filled(stored, vessel);
            if (!filled.isEmpty() && remove(blender, stored.copy(), bottles(stored, vessel))) {
                return filled;
            }
        }
        return ItemStack.EMPTY;
    }

    /** What an empty vessel becomes when a stored liquid is drawn into it; empty if it does not hold that liquid. */
    public static ItemStack filled(ItemStack stored, ItemStack vessel) {
        if (!isStored(stored)) {
            return ItemStack.EMPTY;
        }
        if (stored.is(Items.WATER_BUCKET) && vessel.is(Items.GLASS_BOTTLE)) {
            return PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
        }
        if (stored.is(BakeriesItems.BOTTLE_MILK.get()) && vessel.is(Items.BUCKET)) {
            return new ItemStack(Items.MILK_BUCKET);
        }
        if (!vessel.is(vessel(stored))) {
            return ItemStack.EMPTY;
        }
        ItemStack filled = stored.copyWithCount(1);
        filled.removeTagKey(STORED);
        return filled;
    }

    private static int bottles(ItemStack stored, ItemStack vessel) {
        boolean byBottle = stored.is(Items.WATER_BUCKET) || stored.is(BakeriesItems.BOTTLE_MILK.get());
        return byBottle && vessel.is(Items.BUCKET) ? BOTTLES_PER_BUCKET : 1;
    }

    private static boolean remove(BlenderBlockEntity blender, ItemStack kind, int count) {
        var inventory = blender.getInventory();
        int there = 0;
        for (int slot = 0; slot < BlenderStation.INGREDIENT_SLOTS; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            there += ItemStack.isSameItemSameTags(stack, kind) ? stack.getCount() : 0;
        }
        if (there < count) {
            return false;
        }
        for (int slot = BlenderStation.INGREDIENT_SLOTS - 1; slot >= 0 && count > 0; slot--) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (ItemStack.isSameItemSameTags(stack, kind)) {
                int taken = Math.min(count, stack.getCount());
                inventory.setStackInSlot(slot, stack.copyWithCount(stack.getCount() - taken));
                count -= taken;
            }
        }
        return true;
    }

    /** Bakeries never uses up a water bucket; water in the bowl is used a bottle a set, as every other liquid. */
    public static void useWater(BlenderBlockEntity blender) {
        var inventory = blender.getInventory();
        for (int slot = 0; slot < BlenderStation.INGREDIENT_SLOTS; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (isStored(stack) && stack.is(Items.WATER_BUCKET)) {
                inventory.setStackInSlot(slot, stack.copyWithCount(stack.getCount() - 1));
            }
        }
    }

    /** Spills what is in the bowl: its vessels are long back with the player. */
    public static void spill(BlenderBlockEntity blender) {
        for (int slot = 0; slot < BlenderStation.INGREDIENT_SLOTS; slot++) {
            if (isStored(blender.getInventory().getStackInSlot(slot))) {
                blender.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    public static Component name(ItemStack stored) {
        return stored.is(Items.WATER_BUCKET) ? Blocks.WATER.getName() : stored.getHoverName();
    }

    private static Item vessel(ItemStack full) {
        if (!full.hasCraftingRemainingItem()) {
            return Items.AIR;
        }
        Item remainder = full.getCraftingRemainingItem().getItem();
        return remainder == Items.GLASS_BOTTLE || remainder == Items.BUCKET ? remainder : Items.AIR;
    }
}
