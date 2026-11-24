package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.SlotRef;
import com.renyigesai.bakeries.recipe.BlenderRecipe;
import github.com.gengyoubo.immersivebakery.blender.BlenderLiquids;
import github.com.gengyoubo.immersivebakery.blender.BlenderRecipes;
import github.com.gengyoubo.immersivebakery.blender.BlenderStation;
import github.com.gengyoubo.immersivebakery.client.recipebook.RecipeBook;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The blender's book: what it mixes, the container the batter is scooped with and the time a set takes. */
final class BlenderBook implements RecipeBook.Source {
    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (BlenderRecipe recipe : BlenderRecipes.all(view.level())) {
            List<ItemStack> foot = new ArrayList<>();
            if (!recipe.getContainer().isEmpty()) {
                foot.add(recipe.getContainer());
            }
            foot.add(new ItemStack(Items.CLOCK));
            Component time = Component.translatable("immersive_bakery.book.seconds", BlenderStation.MIX_TICKS / 20);
            pages.add(new RecipeBook.Page(recipe.getId(), recipe.getResultItem(null), recipe.getIngredients(), foot, time, null));
        }
        return pages;
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return List.of(view.state().getItems(BlenderStation.INGREDIENTS));
    }

    @Override
    public ItemStack asIngredient(StationView view, ItemStack stack) {
        return BlenderLiquids.isLiquid(stack) ? BlenderLiquids.store(stack) : stack;
    }

    /** A recipe picked in the book goes into the bowl from the inventory: one set, or as many as fit with shift. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(BlenderStation.RECIPE, page.key().toString());
        fill.putBoolean(BlenderStation.ALL, view.isShiftDown());
        view.sendAction(BlenderStation.ACTION_FILL, fill);
    }

    @Override
    public Object current(StationView view) {
        BlenderRecipe recipe = BlenderRecipes.find(view.level(), Stream.of(view.state().getItems(BlenderStation.INGREDIENTS)).toList());
        if (recipe != null) {
            return recipe.getId();
        }
        ItemStack result = view.state().getItem(SlotRef.of(BlenderStation.OUTPUT, 0));
        return result.isEmpty() ? null : BlenderRecipes.all(view.level()).stream()
                .filter(made -> made.getResultItem(null).is(result.getItem())).map(BlenderRecipe::getId).findFirst().orElse(null);
    }

    @Override
    public String memory() {
        return "blender";
    }
}
