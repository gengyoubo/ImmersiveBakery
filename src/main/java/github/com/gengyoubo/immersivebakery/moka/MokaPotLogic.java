package github.com.gengyoubo.immersivebakery.moka;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.renyigesai.bakeries.block.moka_pot.MokaPotBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class MokaPotLogic implements StationLogic {
    @Nullable
    private final MokaPotBlockEntity pot;

    MokaPotLogic(StationContext station) {
        pot = station.blockEntity() instanceof MokaPotBlockEntity blockEntity ? blockEntity : null;
        if (pot != null) {
            station.state().bindSlots(MokaPotStation.GROUNDS, new Basket(pot));
        }
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        refresh(station);
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        // Despite its name, isInventoryFull tells that the pot is empty.
        return isPresent(station) && !pot.getFill() && pot.isInventoryFull();
    }

    /** As in Bakeries, coffee that went into the pot stays there. */
    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return false;
    }

    @Override
    public void tick(StationContext station) {
        refresh(station);
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (MokaPotStation.ACTION_TAKE.equals(action) && isPresent(station) && MokaPotStation.take(pot, player)
                && player instanceof ServerPlayer serverPlayer) {
            Diegesis.close(serverPlayer);
        }
    }

    private void refresh(StationContext station) {
        if (isPresent(station)) {
            var state = station.state();
            state.setControl(MokaPotStation.PROGRESS, Mth.clamp((float) pot.getCookingTotalTime() / MokaPotStation.BREW_TICKS, 0, 1));
            state.setControl(MokaPotStation.HEATED, pot.isCraft(station.level(), station.pos()) ? 1 : 0);
            state.setControl(MokaPotStation.FILLED, pot.getFill() ? 1 : 0);
        }
    }

    private boolean isPresent(StationContext station) {
        return pot != null && !pot.isRemoved() && station.blockEntity() == pot;
    }

    private record Basket(MokaPotBlockEntity pot) implements SlotStorage {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack get(int slot) {
            return ((MokaPotContents) pot).immersiveBakery$inventory().getStackInSlot(slot);
        }

        @Override
        public void set(int slot, ItemStack stack) {
            if (ItemStack.matches(get(slot), stack)) {
                return;
            }
            pot.addGroundCoffee(stack.copy());
            pot.setChanged();
            var level = pot.getLevel();
            var state = pot.getBlockState();
            level.sendBlockUpdated(pot.getBlockPos(), state, state, 3);
            if (!stack.isEmpty()) {
                level.playSound(null, pot.getBlockPos(), SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.8F, 0.8F);
            }
        }
    }
}
