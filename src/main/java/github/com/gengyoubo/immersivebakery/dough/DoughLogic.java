package github.com.gengyoubo.immersivebakery.dough;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.station.StationContext;
import github.com.gengyoubo.immersivebakery.storage.DrawerLogic;
import github.com.gengyoubo.immersivebakery.storage.Drawers;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class DoughLogic extends DrawerLogic {
    private static final SlotRef DOUGH = SlotRef.of(DoughStation.DOUGH, 0);

    DoughLogic(StationContext station) {
        super(station);
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        super.onOpen(station, player);
        player.awardStat(Stats.INTERACT_WITH_LOOM);
    }

    /** The board takes dough once what was made on it is taken off. */
    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (slot.group().equals(Drawers.ITEMS)) {
            return super.mayPlace(station, player, slot, stack);
        }
        return slot.group().equals(DoughStation.DOUGH) && isCleared(station) && !DoughShaping.recipes(station.level(), stack).isEmpty();
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        super.onCustomAction(station, player, action, data);
        if (!isCleared(station)) {
            return;
        }
        switch (action) {
            case DoughStation.ACTION_SHAPE -> shape(station, player, data);
            case DoughStation.ACTION_TAKE -> take(station, player);
            default -> { }
        }
    }

    private static void take(StationContext station, Player player) {
        ItemStack dough = station.state().getItem(DOUGH).copy();
        if (!dough.isEmpty()) {
            // Leaves what finds no room.
            player.getInventory().add(dough);
            station.state().setItem(DOUGH, dough);
        }
    }

    private static void shape(StationContext station, Player player, CompoundTag data) {
        ItemStack dough = station.state().getItem(DOUGH).copy();
        var recipe = DoughShaping.recipe(station.level(), dough, ResourceLocation.tryParse(data.getString(DoughStation.RECIPE)));
        if (recipe == null) {
            return;
        }
        List<ItemStack> products = DoughShaping.shape(station.level(), dough, recipe, DoughStation.PRODUCT_SLOTS);
        if (products.isEmpty()) {
            return;
        }
        station.state().setItem(DOUGH, dough);
        for (int i = 0; i < products.size(); i++) {
            ItemStack product = products.get(i);
            product.onCraftedBy(station.level(), player, product.getCount());
            station.state().setItem(SlotRef.of(DoughStation.PRODUCTS, i), product);
        }
        player.awardRecipes(List.of(recipe));
        station.level().playSound(null, station.pos(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 1.1F);
        station.effect(DoughStation.EFFECT_SHAPED, "work");
    }

    private static boolean isCleared(StationContext station) {
        return Arrays.stream(station.state().getItems(DoughStation.PRODUCTS)).allMatch(ItemStack::isEmpty);
    }
}
