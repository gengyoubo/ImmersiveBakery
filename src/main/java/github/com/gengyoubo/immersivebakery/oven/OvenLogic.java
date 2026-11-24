package github.com.gengyoubo.immersivebakery.oven;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.renyigesai.bakeries.block.oven.OvenBlock;
import com.renyigesai.bakeries.block.oven.OvenBlockEntity;
import com.renyigesai.bakeries.recipe.oven.OvenRecipe;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class OvenLogic implements StationLogic {
    @Nullable
    private final OvenBlockEntity oven;
    private boolean wasLit;

    OvenLogic(StationContext station) {
        oven = station.blockEntity() instanceof OvenBlockEntity blockEntity ? blockEntity : null;
        if (oven != null) {
            station.state().bindSlots(OvenStation.RACK, new RackStorage(oven));
        }
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        if (isPresent(station)) {
            OvenPower.useManualPower(oven);
            wasLit = oven.getBlockState().getValue(OvenBlock.LIT);
            if (!wasLit) {
                setDoorOpen(station, true);
                playSound(station, oven.getOpenSound());
            }
            syncControls(station);
            syncTimer(station);
        }
    }

    @Override
    public void onClose(StationContext station, Player player) {
        if (isPresent(station) && !oven.getBlockState().getValue(OvenBlock.LIT)) {
            setDoorOpen(station, false);
            playSound(station, oven.getCloseSound());
        }
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return canAccessRacks(station) && OvenStation.canBake(station.level(), stack);
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
        boolean lit = oven.getBlockState().getValue(OvenBlock.LIT);
        if (wasLit && !lit && !station.users().isEmpty()) {
            setDoorOpen(station, true);
        }
        wasLit = lit;
        syncControls(station);
        if (station.level().getGameTime() % 5 == 0) {
            syncTimer(station);
        }
    }

    @Override
    public void onButton(StationContext station, Player player, String control) {
        if (!isPresent(station) || !oven.stillValid(player)) {
            return;
        }
        if (OvenStation.POWER.equals(control)) {
            OvenPower.toggle(oven);
            syncControls(station);
            syncTimer(station);
            return;
        }
        int oldTemperature = oven.getTemperature(oven);
        int offset = Math.floorMod(oldTemperature, OvenStation.TEMPERATURE_STEP);
        if (OvenStation.INCREASE.equals(control) && oldTemperature < OvenStation.MAX_TEMPERATURE) {
            oven.addTemperature(oven, OvenStation.TEMPERATURE_STEP - offset);
        } else if (OvenStation.DECREASE.equals(control) && oldTemperature > 0) {
            oven.subTemperature(oven, offset == 0 ? OvenStation.TEMPERATURE_STEP : offset);
        } else {
            return;
        }
        syncControls(station);
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (OvenStation.isTemperatureButton(action)) {
            onButton(station, player, action);
        }
    }

    private boolean isPresent(StationContext station) {
        return oven != null && !oven.isRemoved() && station.blockEntity() == oven;
    }

    private boolean canAccessRacks(StationContext station) {
        return isPresent(station) && !OvenPower.isPowered(oven);
    }

    private void syncControls(StationContext station) {
        station.state().setControl(OvenStation.TEMPERATURE, oven.getTemperature(oven));
        station.state().setControl(OvenStation.POWERED, OvenPower.isPowered(oven) ? 1 : 0);
    }

    private void syncTimer(StationContext station) {
        long longestRemaining = 0;
        long duration = 1;
        for (int slot = 0; slot < oven.getContainerSize(); slot++) {
            if (oven.getItem(slot).isEmpty()) {
                continue;
            }
            int target = oven.max_cooking_times[slot];
            if (target <= 0) {
                target = oven.getCurrentRecipe(slot).map(OvenRecipe::getTime).orElse(-1);
            }
            if (target < 0) {
                continue;
            }
            // Bakeries compares the elapsed count from before the current tick's increment.
            long slotDuration = (long) target + 1;
            long remaining = slotDuration - Math.max(0, oven.cooking_times[slot]);
            if (remaining > longestRemaining || (remaining == longestRemaining && slotDuration > duration)) {
                longestRemaining = remaining;
                duration = slotDuration;
            }
        }
        station.state().setControl(OvenStation.BAKING_REMAINING, (float) longestRemaining / duration);
    }

    private static void setDoorOpen(StationContext station, boolean open) {
        // Bakeries uses block event 0 to animate the door when its menu opens or closes.
        station.level().blockEvent(station.pos(), station.blockState().getBlock(), 0, open ? 0 : 1);
    }

    private static void playSound(StationContext station, SoundEvent sound) {
        station.level().playSound(null, station.pos(), sound, SoundSource.BLOCKS, 0.6F, 1);
    }

    private record RackStorage(OvenBlockEntity oven) implements SlotStorage {
        @Override
        public int size() {
            return oven.getContainerSize();
        }

        @Override
        public ItemStack get(int slot) {
            return oven.getItem(slot);
        }

        @Override
        public void set(int slot, ItemStack stack) {
            if (ItemStack.matches(oven.getItem(slot), stack)) {
                return;
            }
            if (!ItemStack.isSameItemSameTags(oven.getItem(slot), stack)) {
                oven.cooking_times[slot] = 0;
                oven.max_cooking_times[slot] = 0;
            }
            oven.getItemHandler().setStackInSlot(slot, stack.copy());
            if (OvenPower.canCook(oven)) {
                OvenBlockEntity.setFire(oven.getLevel(), oven.getBlockPos(), oven);
            }
            OvenBlockEntity.updateBlock(oven);
        }
    }
}
