package github.com.gengyoubo.immersivebakery.tank;

import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.renyigesai.bakeries.init.BakeriesItems;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class TankLogic implements StationLogic {
    private static final int CHEESE = 4;

    TankLogic(StationContext station) {
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!player.mayBuild() || !player.level().mayInteract(player, station.pos())) {
            return;
        }
        switch (action) {
            case TankStation.ACTION_USE -> useCarried(station, player);
            case TankStation.ACTION_TAKE -> take(station, player);
            case TankStation.ACTION_FILL -> fill(station, player, TankRecipe.byId(ResourceLocation.tryParse(data.getString(TankStation.RECIPE))));
            default -> { }
        }
    }

    /** All that a recipe still lacks goes in from the inventory. */
    private static void fill(StationContext station, Player player, @Nullable TankRecipe recipe) {
        var inventory = player.getInventory();
        List<Integer> slots = recipe == null ? null : recipe.missingFrom(station.blockState(), inventory);
        if (slots == null) {
            return;
        }
        for (int slot : slots) {
            ItemStack one = inventory.getItem(slot).copyWithCount(1);
            var change = TankContents.use(station.blockState(), one);
            if (change == null) {
                return;
            }
            if (!player.getAbilities().instabuild) {
                inventory.removeItem(slot, 1);
            }
            give(player, change.returned());
            apply(station, change, one);
        }
        inventory.setChanged();
    }

    private static void useCarried(StationContext station, Player player) {
        ItemStack carried = player.inventoryMenu.getCarried();
        BlockState tank = station.blockState();
        var change = carried.isEmpty() ? null : TankContents.use(tank, carried);
        if (change == null) {
            return;
        }
        ItemStack one = carried.copyWithCount(1);
        ItemStack rest = player.getAbilities().instabuild ? carried : carried.copyWithCount(carried.getCount() - 1);
        boolean toCursor = rest.isEmpty() && !change.given();
        player.inventoryMenu.setCarried(toCursor ? change.returned() : rest);
        if (!toCursor) {
            give(player, change.returned());
        }
        apply(station, change, one);
    }

    private static void take(StationContext station, Player player) {
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            return;
        }
        BlockState tank = station.blockState();
        if (TankContents.isCheese(tank)) {
            station.level().setBlock(station.pos(), TankContents.empty(), Block.UPDATE_ALL);
            station.level().playSound(null, station.pos(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 1);
            give(player, new ItemStack(BakeriesItems.CHEESE_CUBE.get(), CHEESE));
        } else if (TankContents.isYeast(tank)) {
            for (ItemStack stack : player.getInventory().items) {
                var change = stack.isEmpty() ? null : TankContents.use(tank, stack);
                if (change != null) {
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    player.getInventory().setChanged();
                    give(player, change.returned());
                    apply(station, change, ItemStack.EMPTY);
                    return;
                }
            }
        }
    }

    private static void apply(StationContext station, TankContents.Change change, ItemStack used) {
        station.level().setBlock(station.pos(), change.tank(), Block.UPDATE_ALL);
        station.level().playSound(null, station.pos(), change.sound(), SoundSource.PLAYERS, 0.8F, 0.8F);
        if (change.given()) {
            CompoundTag data = new CompoundTag();
            data.put(TankStation.ITEM, used.save(new CompoundTag()));
            station.effect(TankStation.EFFECT_ADDED, "tank", data);
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
