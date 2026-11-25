package github.com.gengyoubo.immersivebakery.basket;

import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.renyigesai.bakeries.block.bread_basket.BreadBasketBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class BreadBasketLogic implements StationLogic {
    @Nullable
    private final BreadBasketBlockEntity basket;
    /** Something was taken or came down: what was made smaller to fit may have room for its own size. */
    private boolean roomier;

    BreadBasketLogic(StationContext station) {
        basket = station.blockEntity() instanceof BreadBasketBlockEntity blockEntity ? blockEntity : null;
        if (basket != null) {
            station.state().bindSlots(BreadBasketStation.BREADS, SlotStorage.of(basket, 0, BreadBasketStation.SLOT_COUNT));
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

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (isPresent(station)) {
            BreadBasketStation.items(station.state()).act(player.getInventory(), action, data);
        }
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        roomier |= station.state().getItem(slot).isEmpty();
    }

    @Override
    public void onPoseChanged(StationContext station, Player player, SlotRef slot, ItemPose pose) {
        roomier = true;
    }

    @Override
    public void tick(StationContext station) {
        if (roomier && !station.level().isClientSide() && isPresent(station)) {
            roomier = false;
            BreadBasketStation.items(station.state()).growBack(pose -> BreadBasketStation.HEAPED);
        }
    }

    private boolean isPresent(StationContext station) {
        return basket != null && !basket.isRemoved() && station.blockEntity() == basket;
    }
}
