package github.com.gengyoubo.immersivebakery.rack;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.renyigesai.bakeries.block.bread_rack.BreadRackBlockEntity;
import com.renyigesai.bakeries.block.bread_rack.GlassBreadRackBlock;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class BreadRackLogic implements StationLogic {
    @Nullable
    private final BreadRackBlockEntity rack;

    BreadRackLogic(StationContext station) {
        rack = station.blockEntity() instanceof BreadRackBlockEntity blockEntity ? blockEntity : null;
        if (rack != null) {
            station.state().bindSlots(BreadRackStation.SHELVES, new Shelves(rack));
        }
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        setWindowOpen(station, true);
    }

    @Override
    public void onClose(StationContext station, Player player) {
        if (station.users().stream().allMatch(user -> user == player)) {
            setWindowOpen(station, false);
        }
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return isPresent(station);
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return isPresent(station);
    }

    private boolean isPresent(StationContext station) {
        return rack != null && !rack.isRemoved() && station.blockEntity() == rack;
    }

    private void setWindowOpen(StationContext station, boolean open) {
        var state = station.blockState();
        if (!isPresent(station) || !state.hasProperty(GlassBreadRackBlock.OPEN) || state.getValue(GlassBreadRackBlock.OPEN) == open) {
            return;
        }
        // As GlassBreadRackBlock does when the window is clicked.
        station.level().blockEvent(station.pos(), state.getBlock(), 0, open ? 0 : 1);
        rack.setOpen(open);
        station.level().playSound(null, station.pos(), open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                SoundSource.PLAYERS, 0.8F, 0.8F);
    }

    private record Shelves(BreadRackBlockEntity rack) implements SlotStorage {
        @Override
        public int size() {
            return BreadRackStation.SLOT_COUNT;
        }

        @Override
        public ItemStack get(int slot) {
            return rack.getItem(slot);
        }

        @Override
        public void set(int slot, ItemStack stack) {
            if (!ItemStack.matches(rack.getItem(slot), stack)) {
                rack.setItem(slot, stack.copy());
            }
        }
    }
}
