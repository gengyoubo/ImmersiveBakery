package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.StationView;
import com.renyigesai.bakeries.init.BakeriesItems;
import com.renyigesai.bakeries.recipe.flour_sieve.FlourSieveRecipe;
import github.com.gengyoubo.immersivebakery.client.recipebook.RecipeBook;
import github.com.gengyoubo.immersivebakery.sieve.FlourSieve;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The sieve's book: what can be sifted. {@code put} takes a page whose ingredient is to go onto the sieve. */
record FlourSieveBook(Supplier<ItemStack> onSieve, Consumer<RecipeBook.Page> put) implements RecipeBook.Source {
    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        return view.level().getRecipeManager().getAllRecipesFor(FlourSieveRecipe.Type.INSTANCE).stream()
                .map(recipe -> new RecipeBook.Page(recipe.getId(), recipe.getResultItem(view.level().registryAccess()).copy(), recipe.getIngredients(),
                        List.of(), Component.empty(), null))
                .toList();
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return List.of(onSieve.get());
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        put.accept(page);
    }

    @Override
    public Object current(StationView view) {
        return FlourSieve.recipe(onSieve.get(), view.level()).map(FlourSieveRecipe::getId).orElse(null);
    }

    /** Anything can be put on in place of what is there. */
    @Override
    public boolean fits(StationView view, Object key) {
        return true;
    }

    @Override
    public Item icon(StationView view) {
        return BakeriesItems.FLOUR_SIEVE.get();
    }

    @Override
    public String memory() {
        return "flour_sieve";
    }
}
