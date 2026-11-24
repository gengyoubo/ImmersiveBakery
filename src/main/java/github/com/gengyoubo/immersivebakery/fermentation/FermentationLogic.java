package github.com.gengyoubo.immersivebakery.fermentation;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.renyigesai.bakeries.block.fermentation_box.FermentationBoxBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class FermentationLogic implements StationLogic {
    @Nullable
    private final FermentationBoxBlockEntity box;
    private boolean doorOpen;

    FermentationLogic(StationContext station) {
        box = station.blockEntity() instanceof FermentationBoxBlockEntity blockEntity ? blockEntity : null;
        if (box != null) {
            station.state().bindSlots(FermentationStation.RACK, new RackStorage(box));
        }
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        if (!isPresent(station)) {
            return;
        }
        FermentationPower.useManualPower(box);
        setDoorOpen(station, !FermentationPower.isPowered(box));
        syncControls(station);
        syncTimer(station);
    }

    @Override
    public void onClose(StationContext station, Player player) {
        if (isPresent(station)) {
            setDoorOpen(station, false);
        }
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return canAccessRacks(station) && FermentationStation.canFerment(station.level(), stack);
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return canAccessRacks(station);
    }

    @Override
    public void tick(StationContext station) {
        if (!isPresent(station)) {
            return;
        }
        setDoorOpen(station, !station.users().isEmpty() && !FermentationPower.isPowered(box));
        syncControls(station);
        if (station.level().getGameTime() % 5 == 0) {
            syncTimer(station);
        }
    }

    @Override
    public void onButton(StationContext station, Player player, String control) {
        if (!isPresent(station) || !box.stillValid(player)) {
            return;
        }
        switch (control) {
            case FermentationStation.POWER -> {
                FermentationPower.toggle(box);
                setDoorOpen(station, !FermentationPower.isPowered(box));
            }
            case FermentationStation.DECREASE -> adjustDuration(-FermentationStation.DURATION_STEP);
            case FermentationStation.INCREASE -> adjustDuration(FermentationStation.DURATION_STEP);
            default -> { return; }
        }
        syncControls(station);
        syncTimer(station);
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (FermentationStation.isDurationButton(action)) {
            onButton(station, player, action);
        }
    }

    private void adjustDuration(int step) {
        int previous = box.getFermentationMaxTime();
        int duration = Mth.clamp(previous + step, FermentationStation.MIN_DURATION, FermentationStation.MAX_DURATION);
        if (duration != previous) {
            box.setFermentationMaxTime(box, duration);
        }
    }

    private boolean isPresent(StationContext station) {
        return box != null && !box.isRemoved() && station.blockEntity() == box;
    }

    private boolean canAccessRacks(StationContext station) {
        return isPresent(station) && !FermentationPower.isPowered(box);
    }

    private void syncControls(StationContext station) {
        station.state().setControl(FermentationStation.POWERED, FermentationPower.isPowered(box) ? 1 : 0);
        station.state().setControl(FermentationStation.DURATION, box.getFermentationMaxTime());
        station.state().setControl(FermentationStation.TEMPERATURE, box.getTemperature());
        station.state().setControl(FermentationStation.PERFECT_TIME, box.getPerfectTime());
    }

    private void syncTimer(StationContext station) {
        // Bakeries transforms the dough on the tick after it reaches the selected duration.
        int duration = box.getFermentationMaxTime() + 1;
        int remaining = 0;
        if (duration > FermentationStation.MIN_DURATION) {
            for (int slot = 0; slot < box.getContainerSize(); slot++) {
                var stack = box.getItem(slot);
                int elapsed = box.getFermentationTime()[slot];
                if (FermentationStation.canFerment(station.level(), stack)) {
                    remaining = Math.max(remaining, Math.max(1, duration - elapsed));
                }
            }
        }
        station.state().setControl(FermentationStation.REMAINING, remaining > 0 ? (float) remaining / duration : 0);
    }

    private void setDoorOpen(StationContext station, boolean open) {
        if (doorOpen != open) {
            doorOpen = open;
            station.level().blockEvent(station.pos(), station.blockState().getBlock(), 0, open ? 0 : 1);
            station.level().playSound(null, station.pos(), open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundSource.BLOCKS, 0.6F, 1);
        }
    }

    private record RackStorage(FermentationBoxBlockEntity box) implements SlotStorage {
        @Override
        public int size() {
            return box.getContainerSize();
        }

        @Override
        public ItemStack get(int slot) {
            return box.getItem(slot);
        }

        @Override
        public void set(int slot, ItemStack stack) {
            if (!ItemStack.matches(box.getItem(slot), stack)) {
                box.setItem(slot, stack.copy());
                box.updateBlock();
            }
        }
    }
}
