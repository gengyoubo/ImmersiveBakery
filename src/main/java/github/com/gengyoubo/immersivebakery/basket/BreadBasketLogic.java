package github.com.gengyoubo.immersivebakery.basket;

import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.renyigesai.bakeries.block.bread_basket.BreadBasketBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class BreadBasketLogic implements StationLogic {
    private static final float SAME_SIZE = 0.01F;

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
        int slot = data.getInt(BreadBasketStation.INVENTORY_SLOT);
        if (!BreadBasketStation.ACTION_STORE.equals(action) || !isPresent(station) || slot < 0 || slot >= player.getInventory().items.size()) {
            return;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        if (!stack.isEmpty()) {
            ItemPose place = data.contains(BreadBasketStation.PLACE) ? ItemPose.load(data.getCompound(BreadBasketStation.PLACE)) : null;
            player.getInventory().setItem(slot, store(station.state(), stack.copy(), place));
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
            growBack(station.state());
        }
    }

    /** What lies on top and was made smaller to fit has its own size again where it fits now. */
    static void growBack(StationState state) {
        for (int i = 0; i < BreadBasketStation.SLOT_COUNT; i++) {
            SlotRef slot = SlotRef.of(BreadBasketStation.BREADS, i);
            ItemPose pose = state.getPose(slot);
            if (pose == null || state.getItem(slot).isEmpty() || pose.scale() > pose.wanted() - SAME_SIZE || isUnderSomething(state, i)) {
                continue;
            }
            float lift = pose.lift() * pose.wanted() / pose.scale();
            if (pose.y() + 2 * lift <= BreadBasketStation.HEAPED) {
                // Held up by nothing for now, so that it comes to rest anew.
                state.setPose(slot, new ItemPose(pose.x(), pose.y(), pose.z(), lift, pose.rotation(), pose.wanted(), -1));
            }
        }
    }

    private static boolean isUnderSomething(StationState state, int index) {
        for (int i = 0; i < BreadBasketStation.SLOT_COUNT; i++) {
            ItemPose pose = state.getPose(SlotRef.of(BreadBasketStation.BREADS, i));
            if (pose != null && pose.on() == index) {
                return true;
            }
        }
        return false;
    }

    /** Onto what is there already, then where nothing is, in {@code place} or wherever there is room; what is left over. */
    static ItemStack store(StationState state, ItemStack stack, @Nullable ItemPose place) {
        for (boolean empty : new boolean[] {false, true}) {
            for (int i = 0; i < BreadBasketStation.SLOT_COUNT && !stack.isEmpty(); i++) {
                SlotRef slot = SlotRef.of(BreadBasketStation.BREADS, i);
                ItemStack there = state.getItem(slot);
                if (there.isEmpty() != empty || !empty && !ItemStack.isSameItemSameTags(there, stack)) {
                    continue;
                }
                int moved = Math.min(stack.getCount(), stack.getMaxStackSize() - there.getCount());
                if (moved > 0) {
                    if (empty) {
                        state.setPose(slot, place == null ? null : place.standingOn(-1));
                    }
                    state.setItem(slot, stack.copyWithCount(there.getCount() + moved));
                    stack.shrink(moved);
                }
            }
        }
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    private boolean isPresent(StationContext station) {
        return basket != null && !basket.isRemoved() && station.blockEntity() == basket;
    }
}
