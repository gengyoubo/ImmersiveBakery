package github.com.gengyoubo.immersivebakery.bread;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class BreadLogic implements StationLogic {
    private static final SlotRef KNIFE = SlotRef.of(BreadStation.KNIFE, 0);

    BreadLogic(StationContext station) {
    }

    /** The player's knife lies down beside the bread: the one in the hand, else the first in the inventory. */
    @Override
    public void onOpen(StationContext station, Player player) {
        var inventory = player.getInventory();
        if (station.state().getItem(KNIFE).isEmpty()) {
            int slot = BreadCutting.isKnife(inventory.getSelected()) ? inventory.selected : -1;
            for (int i = 0; i < inventory.getContainerSize() && slot < 0; i++) {
                slot = BreadCutting.isKnife(inventory.getItem(i)) ? i : -1;
            }
            if (slot >= 0) {
                station.state().setItem(KNIFE, inventory.removeItem(slot, 1));
                inventory.setChanged();
            }
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (station.state().getItem(KNIFE).isEmpty() || !player.mayBuild() || !player.level().mayInteract(player, station.pos())) {
            return;
        }
        BlockState bread = station.blockState();
        if (BreadStation.ACTION_LOOSEN.equals(action) && BreadCutting.isInMould(bread)) {
            station.effect(BreadStation.EFFECT_LOOSENED, "bread");
            apply(station, player, BreadCutting.loosen(bread), SoundEvents.LANTERN_BREAK);
            setCut(station, 0);
        } else if (BreadStation.ACTION_CUT.equals(action) && BreadCutting.isCuttable(bread)) {
            int line = data.getInt(BreadStation.LINE);
            int cut = station.state().data().getInt(BreadStation.CUT);
            if (!BreadCutting.lines(bread).contains(line) || (cut & 1 << line) != 0) {
                return;
            }
            setCut(station, cut | 1 << line);
            station.effect(BreadStation.EFFECT_CUT, "bread");
            apply(station, player, BreadCutting.cut(bread, cut | 1 << line), SoundEvents.WOOL_STEP);
        }
    }

    private static void setCut(StationContext station, int cut) {
        CompoundTag data = station.state().data();
        data.putInt(BreadStation.CUT, cut);
        station.state().setData(data);
    }

    private static void apply(StationContext station, Player player, BreadCutting.Result result, SoundEvent sound) {
        station.level().playSound(null, station.pos(), sound, SoundSource.BLOCKS, 0.8F, 0.8F);
        ItemStack knife = station.state().getItem(KNIFE).copy();
        knife.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        station.state().setItem(KNIFE, knife);
        for (ItemStack stack : result.given()) {
            ItemStack given = stack.copy();
            if (!player.getInventory().add(given)) {
                player.drop(given, false);
            }
        }
        // Last: with the bread the station may be gone.
        if (result.left() != station.blockState()) {
            station.level().setBlock(station.pos(), result.left(), Block.UPDATE_ALL);
        }
    }
}
