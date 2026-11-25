package github.com.gengyoubo.immersivebakery.tank;

import com.renyigesai.bakeries.block.FermentationTankBlock;
import com.renyigesai.bakeries.block.MilkTankBlock;
import com.renyigesai.bakeries.block.YeastTankBlock;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import com.renyigesai.bakeries.init.BakeriesItems;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** What Bakeries' tanks, which keep all they hold in their block states, take and give. */
public final class TankContents {
    public static final TagKey<Item> SALT = ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "salt"));
    public static final int MOST_FLOUR = 3;
    public static final int MOST_MILK = 3;

    private TankContents() {
    }

    /**
     * What one item does to a tank.
     *
     * @param tank     the tank afterwards
     * @param returned what the player gets for the item
     * @param given    whether the item is used up on it, rather than filled
     */
    public record Change(BlockState tank, ItemStack returned, boolean given, SoundEvent sound) {
    }

    /** What happens when one of these goes to the tank; null when the tank has no use for it. */
    @Nullable
    public static Change use(BlockState tank, ItemStack stack) {
        if (isPlain(tank)) {
            int flour = flour(tank);
            boolean water = hasWater(tank);
            if (flour < MOST_FLOUR && stack.is(BakeriesItems.WHOLE_WHEAT_FLOUR.get())) {
                return new Change(tank.setValue(FermentationTankBlock.FLOUR, flour + 1), ItemStack.EMPTY, true, SoundEvents.WOOL_STEP);
            }
            if (!water && stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER) {
                return new Change(tank.setValue(FermentationTankBlock.WATER, true), new ItemStack(Items.GLASS_BOTTLE), true,
                        SoundEvents.BOTTLE_EMPTY);
            }
            // Bakeries lets milk take the place of water that is in there.
            return flour == 0 && !water && stack.is(Items.MILK_BUCKET)
                    ? new Change(BakeriesBlocks.Milk_TANK.get().defaultBlockState(), new ItemStack(Items.BUCKET), true, SoundEvents.BUCKET_EMPTY)
                    : null;
        }
        if (isMilk(tank)) {
            int milk = tank.getValue(MilkTankBlock.MILK);
            if (tank.getValue(MilkTankBlock.SALT)) {
                // Bakeries has no tank of less milk with salt in it.
                return null;
            }
            if (stack.is(Items.GLASS_BOTTLE)) {
                return new Change(milk > 1 ? tank.setValue(MilkTankBlock.MILK, milk - 1) : empty(), new ItemStack(BakeriesItems.BOTTLE_MILK.get()),
                        false, SoundEvents.BOTTLE_FILL);
            }
            if (milk < MOST_MILK && stack.is(BakeriesItems.BOTTLE_MILK.get())) {
                return new Change(tank.setValue(MilkTankBlock.MILK, milk + 1), new ItemStack(Items.GLASS_BOTTLE), true, SoundEvents.BOTTLE_EMPTY);
            }
            return milk == MOST_MILK && stack.is(SALT)
                    ? new Change(tank.setValue(MilkTankBlock.SALT, true), ItemStack.EMPTY, true, SoundEvents.SAND_PLACE) : null;
        }
        if (isYeast(tank) && stack.is(Items.GLASS_BOTTLE)) {
            int yeast = tank.getValue(YeastTankBlock.YEAST);
            return new Change(yeast > 1 ? tank.setValue(YeastTankBlock.YEAST, yeast - 1) : empty(), new ItemStack(BakeriesItems.BOTTLE_YEAST.get()),
                    false, SoundEvents.BOTTLE_FILL);
        }
        return null;
    }

    /** Cheese comes out by hand. */
    public static boolean isCheese(BlockState tank) {
        return tank.is(BakeriesBlocks.CHEESE_TANK.get());
    }

    public static boolean isYeast(BlockState tank) {
        return tank.is(BakeriesBlocks.YEAST_TANK.get());
    }

    /** The tank that is empty or takes flour and water. */
    public static boolean isPlain(BlockState tank) {
        return tank.is(BakeriesBlocks.FERMENTATION_TANK.get());
    }

    public static boolean isMilk(BlockState tank) {
        return tank.is(BakeriesBlocks.Milk_TANK.get());
    }

    /** All that it needs is in, and what it makes takes its time. */
    public static boolean isFermenting(BlockState tank) {
        return isPlain(tank) ? flour(tank) == MOST_FLOUR && hasWater(tank)
                : isMilk(tank) && tank.getValue(MilkTankBlock.SALT);
    }

    /** What a fermenting tank becomes. */
    public static BlockState fermented(BlockState tank) {
        return (isMilk(tank) ? BakeriesBlocks.CHEESE_TANK : BakeriesBlocks.YEAST_TANK).get().defaultBlockState();
    }

    public static int flour(BlockState tank) {
        return tank.getValue(FermentationTankBlock.FLOUR);
    }

    public static boolean hasWater(BlockState tank) {
        return tank.getValue(FermentationTankBlock.WATER);
    }

    public static BlockState empty() {
        return BakeriesBlocks.FERMENTATION_TANK.get().defaultBlockState();
    }
}
