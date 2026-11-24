package github.com.gengyoubo.immersivebakery.kiln;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.renyigesai.bakeries.block.stone_kiln.StoneKilnBlock;
import com.renyigesai.bakeries.block.stone_kiln.StoneKilnBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class KilnLogic implements StationLogic {
    private static final SlotRef PEEL = SlotRef.of(KilnStation.PEEL, 0);
    @Nullable
    private final StoneKilnBlockEntity kiln;

    KilnLogic(StationContext station) {
        kiln = station.blockEntity() instanceof StoneKilnBlockEntity blockEntity ? blockEntity : null;
        station.state().bindSlots(KilnStation.LOAD, new Blade(station.state()));
        if (kiln != null) {
            station.state().bindSlots(KilnStation.HEARTH, new Hearth(kiln));
        }
    }

    /** The player's peel lies down before the kiln: the one in the hand, else the first in the inventory. */
    @Override
    public void onOpen(StationContext station, Player player) {
        if (!isPresent(station)) {
            return;
        }
        var inventory = player.getInventory();
        if (station.state().getItem(PEEL).isEmpty()) {
            int slot = Peel.isPeel(inventory.getSelected()) ? inventory.selected : -1;
            for (int i = 0; i < inventory.getContainerSize() && slot < 0; i++) {
                slot = Peel.isPeel(inventory.getItem(i)) ? i : -1;
            }
            if (slot >= 0) {
                station.state().setItem(PEEL, inventory.removeItemNoUpdate(slot));
                inventory.setChanged();
            }
        }
        refresh(station);
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return switch (slot.group()) {
            case KilnStation.PEEL -> isPresent(station);
            case KilnStation.LOAD -> isPresent(station) && !station.state().getItem(PEEL).isEmpty() && Peel.isRaw(station.level(), stack);
            default -> false;
        };
    }

    /** What is in the kiln only comes out on the peel. */
    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return isPresent(station) && !slot.group().equals(KilnStation.HEARTH);
    }

    @Override
    public void tick(StationContext station) {
        refresh(station);
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!isPresent(station)) {
            return;
        }
        switch (action) {
            case KilnStation.ACTION_REACH -> reach(station);
            case KilnStation.ACTION_TURN -> turn(station);
            default -> { return; }
        }
        refresh(station);
    }

    /** The blade is in the kiln: it leaves what it carries there or takes out what is done. */
    private void reach(StationContext station) {
        ItemStack peel = station.state().getItem(PEEL).copy();
        ItemStack load = Peel.load(peel);
        if (peel.isEmpty()) {
            return;
        }
        if (kiln.isEmpty()) {
            if (!load.isEmpty() && kiln.addItem(load.copy())) {
                Peel.setLoad(peel, ItemStack.EMPTY);
                station.state().setItem(PEEL, peel);
                done(station, KilnStation.EFFECT_PUT, SoundEvents.WOOL_STEP);
            }
        } else if (load.isEmpty() && isDone(station)) {
            Peel.setLoad(peel, kiln.getItem(0));
            station.state().setItem(PEEL, peel);
            kiln.getInventory().setStackInSlot(0, ItemStack.EMPTY);
            kiln.initialize();
            sync();
            done(station, KilnStation.EFFECT_TAKEN, SoundEvents.WOOL_STEP);
        }
    }

    private void turn(StationContext station) {
        ItemStack peel = station.state().getItem(PEEL);
        if (!peel.isEmpty() && Peel.load(peel).isEmpty() && kiln.isTurnOver()) {
            kiln.startTurnOver();
            sync();
            done(station, KilnStation.EFFECT_TURNED, SoundEvents.WOOL_STEP);
        }
    }

    private void sync() {
        kiln.setChanged();
        kiln.getLevel().sendBlockUpdated(kiln.getBlockPos(), kiln.getBlockState(), kiln.getBlockState(), 3);
    }

    private static void done(StationContext station, ResourceLocation effect, SoundEvent sound) {
        station.level().playSound(null, station.pos(), sound, SoundSource.BLOCKS, 0.8F, 0.8F);
        station.effect(effect, "peel");
    }

    private boolean isDone(StationContext station) {
        return !kiln.isEmpty() && !Peel.isRaw(station.level(), kiln.getItem(0));
    }

    private void refresh(StationContext station) {
        if (!isPresent(station)) {
            return;
        }
        StationState state = station.state();
        state.setControl(KilnStation.WANTS_TURN, kiln.isTurnOver() ? 1 : 0);
        state.setControl(KilnStation.DONE, isDone(station) ? 1 : 0);
        state.setControl(KilnStation.LIT, kiln.getBlockState().getValue(StoneKilnBlock.LIT) ? 1 : 0);
    }

    private boolean isPresent(StationContext station) {
        return kiln != null && !kiln.isRemoved() && station.blockEntity() == kiln;
    }

    /** The one place on the peel that lies here. */
    private record Blade(StationState state) implements SlotStorage {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack get(int slot) {
            return Peel.load(state.getItem(PEEL));
        }

        @Override
        public void set(int slot, ItemStack stack) {
            ItemStack peel = state.getItem(PEEL).copy();
            if (Peel.isPeel(peel) && !ItemStack.matches(Peel.load(peel), stack)) {
                Peel.setLoad(peel, stack);
                state.setItem(PEEL, peel);
            }
        }
    }

    private record Hearth(StoneKilnBlockEntity kiln) implements SlotStorage {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack get(int slot) {
            return kiln.getItem(0);
        }

        @Override
        public void set(int slot, ItemStack stack) {
            // Only the peel reaches in here, see KilnLogic.reach.
        }
    }
}
