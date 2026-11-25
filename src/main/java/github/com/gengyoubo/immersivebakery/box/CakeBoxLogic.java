package github.com.gengyoubo.immersivebakery.box;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.renyigesai.bakeries.block.cake_box.CakeBoxBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class CakeBoxLogic implements StationLogic {
    @Nullable
    private final CakeBoxBlockEntity box;

    CakeBoxLogic(StationContext station) {
        box = station.blockEntity() instanceof CakeBoxBlockEntity blockEntity ? blockEntity : null;
        if (box != null) {
            station.state().bindSlots(CakeBoxStation.CAKE, new Inside(box));
        }
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        shut(station);
    }

    @Override
    public void onClose(StationContext station, Player player) {
        shut(station);
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return isOpen(station);
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return isOpen(station);
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!isPresent(station)) {
            return;
        }
        boolean untied = station.state().getControl(CakeBoxStation.UNTIED) > 0;
        boolean open = station.state().getControl(CakeBoxStation.OPEN) > 0;
        switch (action) {
            case CakeBoxStation.ACTION_UNTIE -> {
                if (!untied) {
                    station.state().setControl(CakeBoxStation.UNTIED, 1);
                    sound(station, SoundEvents.WOOL_BREAK, 1.3F);
                }
            }
            case CakeBoxStation.ACTION_OPEN -> {
                // The lid is tied down.
                if (untied && !open) {
                    station.state().setControl(CakeBoxStation.OPEN, 1);
                    sound(station, SoundEvents.BARREL_OPEN, 1.5F);
                }
            }
            case CakeBoxStation.ACTION_CLOSE -> {
                if (open) {
                    station.state().setControl(CakeBoxStation.OPEN, 0);
                    sound(station, SoundEvents.BARREL_CLOSE, 1.5F);
                }
            }
            default -> { }
        }
    }

    private static void shut(StationContext station) {
        station.state().setControl(CakeBoxStation.OPEN, 0);
        station.state().setControl(CakeBoxStation.UNTIED, 0);
    }

    private static void sound(StationContext station, SoundEvent sound, float pitch) {
        station.level().playSound(null, station.pos(), sound, SoundSource.BLOCKS, 0.5F, pitch);
    }

    private boolean isOpen(StationContext station) {
        return isPresent(station) && station.state().getControl(CakeBoxStation.OPEN) > 0;
    }

    private boolean isPresent(StationContext station) {
        return box != null && !box.isRemoved() && station.blockEntity() == box;
    }

    private record Inside(CakeBoxBlockEntity box) implements SlotStorage {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack get(int slot) {
            return box.getInventory().getStackInSlot(slot);
        }

        @Override
        public void set(int slot, ItemStack stack) {
            if (!ItemStack.matches(get(slot), stack)) {
                box.getInventory().setStackInSlot(slot, stack.copy());
                box.setChanged();
                box.getLevel().sendBlockUpdated(box.getBlockPos(), box.getBlockState(), box.getBlockState(), 3);
            }
        }
    }
}
