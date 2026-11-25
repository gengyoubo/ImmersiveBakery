package github.com.gengyoubo.immersivebakery.storage;

import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import java.util.function.IntPredicate;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Items that lie about freely in a station: so many slots of a group, from the first of them on. */
public record PlacedItems(StationState state, String group, int first, int count) {
    /** A stack of the inventory goes in; data: {@link #SLOT}, its slot there, and {@link #PLACE}, where it lies if it is put apart. */
    public static final String ACTION_STORE = "store";
    /** An item that came without a place gets one; data: {@link #SLOT}, its slot here, and {@link #PLACE}. */
    public static final String ACTION_ARRANGE = "arrange";
    public static final String SLOT = "slot";
    public static final String PLACE = "place";
    private static final float SAME_SIZE = 0.01F;

    public SlotRef slot(int index) {
        return SlotRef.of(group, first + index);
    }

    public ItemStack item(int index) {
        return state.getItem(slot(index));
    }

    @Nullable
    public ItemPose pose(int index) {
        return state.getPose(slot(index));
    }

    /** Whether it was one of the actions of this. */
    public boolean act(Inventory inventory, String action, CompoundTag data) {
        int slot = data.getInt(SLOT);
        ItemPose place = data.contains(PLACE) ? ItemPose.load(data.getCompound(PLACE)) : null;
        switch (action) {
            case ACTION_STORE -> {
                if (slot >= 0 && slot < inventory.items.size() && !inventory.getItem(slot).isEmpty()) {
                    inventory.setItem(slot, store(inventory.getItem(slot).copy(), place));
                }
            }
            case ACTION_ARRANGE -> {
                if (slot >= 0 && slot < count && place != null && !item(slot).isEmpty() && pose(slot) == null) {
                    state.setPose(slot(slot), place.standingOn(-1));
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Onto what is there already, then where nothing is, in {@code place} or wherever there is room; what is left over. */
    public ItemStack store(ItemStack stack, @Nullable ItemPose place) {
        for (boolean empty : new boolean[] {false, true}) {
            for (int i = 0; i < count && !stack.isEmpty(); i++) {
                ItemStack there = item(i);
                if (there.isEmpty() != empty || !empty && !ItemStack.isSameItemSameTags(there, stack)) {
                    continue;
                }
                int moved = Math.min(stack.getCount(), stack.getMaxStackSize() - there.getCount());
                if (moved > 0) {
                    if (empty) {
                        state.setPose(slot(i), place == null ? null : place.standingOn(-1));
                        // For one stack alone.
                        place = null;
                    }
                    state.setItem(slot(i), stack.copyWithCount(there.getCount() + moved));
                    stack.shrink(moved);
                }
            }
        }
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    /** What lies on top and was made smaller to fit has its own size again where it fits now, under {@code ceiling}. */
    public void growBack(ToDoubleFunction<ItemPose> ceiling) {
        for (int i = 0; i < count; i++) {
            ItemPose pose = pose(i);
            if (pose == null || item(i).isEmpty() || pose.scale() > pose.wanted() - SAME_SIZE || isUnderSomething(i)) {
                continue;
            }
            float lift = pose.lift() * pose.wanted() / pose.scale();
            if (pose.y() + 2 * lift <= ceiling.applyAsDouble(pose)) {
                // Held up by nothing for now, so that it comes to rest anew.
                state.setPose(slot(i), new ItemPose(pose.x(), pose.y(), pose.z(), lift, pose.rotation(), pose.wanted(), -1));
            }
        }
    }

    private boolean isUnderSomething(int index) {
        for (int i = 0; i < count; i++) {
            ItemPose pose = pose(i);
            if (pose != null && pose.on() == first + index) {
                return true;
            }
        }
        return false;
    }

    /** Where nothing is any more, as when a hopper took it, nothing has a place. */
    public void forgetTaken() {
        forgetTaken(index -> item(index).isEmpty());
    }

    /** As that, where the state may not know of the items yet. */
    public void forgetTaken(IntPredicate isEmpty) {
        for (int i = 0; i < count; i++) {
            if (pose(i) != null && isEmpty.test(i)) {
                forgetPlace(i);
            }
        }
    }

    /** Also of what lay on it, which is set out anew. */
    private void forgetPlace(int index) {
        state.setPose(slot(index), null);
        for (int above = 0; above < count; above++) {
            ItemPose pose = pose(above);
            if (pose != null && pose.on() == first + index) {
                forgetPlace(above);
            }
        }
    }
}
