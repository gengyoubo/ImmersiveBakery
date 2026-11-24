package github.com.gengyoubo.immersivebakery.tank;

import com.renyigesai.bakeries.block.MilkTankBlock;
import com.renyigesai.bakeries.init.BakeriesItems;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

/** What Bakeries' tank makes, which is written in its blocks and not in recipe files. Ingredients go in in this order. */
public enum TankRecipe {
    YEAST(() -> new ItemStack(BakeriesItems.BOTTLE_YEAST.get(), 3), 3) {
        @Override
        public List<Ingredient> ingredients() {
            Ingredient flour = Ingredient.of(BakeriesItems.WHOLE_WHEAT_FLOUR.get());
            return List.of(flour, flour, flour, StrictNBTIngredient.of(water()));
        }

        @Override
        public boolean isMadeIn(BlockState tank) {
            return TankContents.isYeast(tank) || TankContents.isPlain(tank) && (TankContents.flour(tank) > 0 || TankContents.hasWater(tank));
        }
    },
    CHEESE(() -> new ItemStack(BakeriesItems.CHEESE_CUBE.get(), 4), 0) {
        @Override
        public List<Ingredient> ingredients() {
            return List.of(Ingredient.of(Items.MILK_BUCKET), Ingredient.of(TankContents.SALT));
        }

        @Override
        public boolean isMadeIn(BlockState tank) {
            return TankContents.isCheese(tank) || TankContents.isMilk(tank);
        }
    },
    /** A bucket of milk by the bottle. */
    MILK(() -> new ItemStack(BakeriesItems.BOTTLE_MILK.get(), 3), 3) {
        @Override
        public List<Ingredient> ingredients() {
            return List.of(Ingredient.of(Items.MILK_BUCKET));
        }

        @Override
        public boolean isMadeIn(BlockState tank) {
            return TankContents.isMilk(tank) && !TankContents.isFermenting(tank);
        }
    };

    private final Supplier<ItemStack> product;
    private final int bottles;

    TankRecipe(Supplier<ItemStack> product, int bottles) {
        this.product = product;
        this.bottles = bottles;
    }

    @Nullable
    public static TankRecipe byId(@Nullable ResourceLocation id) {
        return Arrays.stream(values()).filter(recipe -> recipe.id().equals(id)).findFirst().orElse(null);
    }

    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, "fermentation_tank/" + name().toLowerCase(Locale.ROOT));
    }

    public ItemStack product() {
        return product.get();
    }

    /** How many glass bottles it takes to get it out; without any it comes out by hand. */
    public int bottles() {
        return bottles;
    }

    public abstract List<Ingredient> ingredients();

    /** Whether the tank makes this, or has made it. */
    public abstract boolean isMadeIn(BlockState tank);

    /** What went into the tank and still lies there as it was. */
    public static List<ItemStack> lyingIn(BlockState tank) {
        List<ItemStack> in = new ArrayList<>();
        if (TankContents.isPlain(tank)) {
            if (TankContents.flour(tank) > 0) {
                in.add(new ItemStack(BakeriesItems.WHOLE_WHEAT_FLOUR.get(), TankContents.flour(tank)));
            }
            if (TankContents.hasWater(tank)) {
                in.add(water());
            }
        } else if (TankContents.isMilk(tank) && tank.getValue(MilkTankBlock.MILK) == TankContents.MOST_MILK) {
            in.add(new ItemStack(Items.MILK_BUCKET));
            if (TankContents.isFermenting(tank)) {
                in.add(new ItemStack(BakeriesItems.SALT.get()));
            }
        }
        return in;
    }

    /** The slots to take what is still missing from, one item each and in order; null when not all of it is there or fits in. */
    @Nullable
    public List<Integer> missingFrom(BlockState tank, Container inventory) {
        List<ItemStack> in = lyingIn(tank);
        int[] left = new int[inventory.getContainerSize()];
        Arrays.setAll(left, slot -> inventory.getItem(slot).getCount());
        List<Integer> slots = new ArrayList<>();
        BlockState filled = tank;
        for (Ingredient ingredient : ingredients()) {
            ItemStack there = in.stream().filter(stack -> !stack.isEmpty() && ingredient.test(stack)).findFirst().orElse(ItemStack.EMPTY);
            if (!there.isEmpty()) {
                there.shrink(1);
                continue;
            }
            int from = -1;
            for (int slot = 0; slot < left.length && from < 0; slot++) {
                from = left[slot] > 0 && ingredient.test(inventory.getItem(slot)) ? slot : -1;
            }
            var change = from < 0 ? null : TankContents.use(filled, inventory.getItem(from));
            if (change == null || !change.given()) {
                return null;
            }
            left[from]--;
            slots.add(from);
            filled = change.tank();
        }
        return slots;
    }

    private static ItemStack water() {
        return PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
    }
}
