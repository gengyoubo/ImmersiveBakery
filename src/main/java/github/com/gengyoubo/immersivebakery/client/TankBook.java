package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.StationView;
import github.com.gengyoubo.immersivebakery.client.recipebook.RecipeBook;
import github.com.gengyoubo.immersivebakery.tank.TankRecipe;
import github.com.gengyoubo.immersivebakery.tank.TankStation;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/** The tank's book: what it makes, and the bottles that is taken out with. */
final class TankBook implements RecipeBook.Source {
    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        return Arrays.stream(TankRecipe.values()).map(recipe -> {
            boolean byHand = recipe.bottles() == 0;
            List<ItemStack> foot = byHand ? List.of() : List.of(new ItemStack(Items.GLASS_BOTTLE));
            Component note = byHand ? Component.translatable("immersive_bakery.book.by_hand")
                    : Component.literal("× " + recipe.bottles());
            return new RecipeBook.Page(recipe.id(), recipe.product(), recipe.ingredients(), foot, note, null);
        }).toList();
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return TankRecipe.lyingIn(tank(view));
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag data = new CompoundTag();
        data.putString(TankStation.RECIPE, page.key().toString());
        view.sendAction(TankStation.ACTION_FILL, data);
    }

    @Override
    public Object current(StationView view) {
        return Arrays.stream(TankRecipe.values()).filter(recipe -> recipe.isMadeIn(tank(view))).map(TankRecipe::id).findFirst().orElse(null);
    }

    /** Milk is there for cheese or to be taken by the bottle. */
    @Override
    public boolean fits(StationView view, Object key) {
        return current(view) == null || Arrays.stream(TankRecipe.values()).anyMatch(recipe -> recipe.id().equals(key) && recipe.isMadeIn(tank(view)));
    }

    @Override
    public String memory() {
        return "fermentation_tank";
    }

    private static BlockState tank(StationView view) {
        return view.level().getBlockState(view.pos());
    }
}
