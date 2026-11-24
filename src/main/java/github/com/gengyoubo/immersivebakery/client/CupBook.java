package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.StationView;
import com.renyigesai.bakeries.block.glass_drink_cup.GlassDrinkCupBlockEntity;
import github.com.gengyoubo.immersivebakery.client.recipebook.RecipeBook;
import github.com.gengyoubo.immersivebakery.cup.CupContents;
import github.com.gengyoubo.immersivebakery.cup.CupStation;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** The cup's book: the drinks mixed in it. */
final class CupBook implements RecipeBook.Source {
    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        return CupContents.recipes(view.level()).stream().map(recipe -> new RecipeBook.Page(recipe.getId(),
                recipe.getResultItem(view.level().registryAccess()), recipe.getIngredients(), List.of(), null, null)).toList();
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return in(view);
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag data = new CompoundTag();
        data.putString(CupStation.RECIPE, page.key().toString());
        view.sendAction(CupStation.ACTION_FILL, data);
    }

    @Override
    public Object current(StationView view) {
        var made = CupContents.made(view.level(), in(view));
        return made == null ? null : made.getId();
    }

    /** One drink is often the beginning of another. */
    @Override
    public boolean fits(StationView view, Object key) {
        return true;
    }

    @Override
    public String memory() {
        return "drink_cup";
    }

    private static List<ItemStack> in(StationView view) {
        return view.level().getBlockEntity(view.pos()) instanceof GlassDrinkCupBlockEntity cup ? CupContents.in(cup) : List.of();
    }
}
