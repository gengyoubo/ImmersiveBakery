package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.StationState;
import github.com.gengyoubo.immersivebakery.storage.PlacedItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/** Finds places for what is put among freely placed items by itself: from the inventory, or by a hopper. */
final class PlacedItemsView {
    private static final float ANSWER_SECONDS = 1;
    /** How small a thing is made to fit in, and in which steps. */
    private static final float SMALLEST = 0.4F;
    private static final float SMALLER = 0.1F;
    private static final float STEP = 0.5F;
    private static final float TOUCHING = 0.1F;
    private static final float DROP = 0.3F;
    /** As thin as this, a thing lies; Diegesis has it so. */
    private static final float FLAT = 0.2F;

    private final Function<StationState, PlacedItems> items;
    private final float itemSize;
    /** How much things are turned, this way and that. */
    private final float askew;
    private final Area area;
    /** Goes with everything told the server. */
    private final CompoundTag which;
    /** The slot asked a place for, and for how long the answer is waited for. */
    private int asked = -1;
    private float waiting;

    PlacedItemsView(Function<StationState, PlacedItems> items, float itemSize, float askew, Area area, CompoundTag which) {
        this.items = items;
        this.itemSize = itemSize;
        this.askew = askew;
        this.area = area;
        this.which = which;
    }

    /** Where things can lie, scene pixels. */
    record Area(float fromX, float fromZ, float toX, float toZ, float floor, float ceiling) {
    }

    /** A stack of the inventory goes in; whether there is room for it. */
    boolean store(StationView view, int slot, ItemStack stack) {
        CompoundTag data = which.copy();
        PlacedItems there = items.apply(view.state());
        if (!fitsOnto(there, stack)) {
            ItemPose place = hasFreeSlot(there) ? findPlace(there, stack) : null;
            if (place == null) {
                return false;
            }
            data.put(PlacedItems.PLACE, place.save());
        }
        data.putInt(PlacedItems.SLOT, slot);
        view.sendAction(PlacedItems.ACTION_STORE, data);
        return true;
    }

    /** Gives what came without a place one, a thing at a time. */
    void arrange(StationView view, float deltaSeconds) {
        waiting -= deltaSeconds;
        PlacedItems there = items.apply(view.state());
        if (!view.isFocused() || waiting > 0 && asked >= 0 && isLost(there, asked)) {
            return;
        }
        asked = -1;
        for (int i = 0; i < there.count(); i++) {
            ItemPose place = isLost(there, i) ? findPlace(there, there.item(i)) : null;
            if (place != null) {
                asked = i;
                waiting = ANSWER_SECONDS;
                CompoundTag data = which.copy();
                data.putInt(PlacedItems.SLOT, i);
                data.put(PlacedItems.PLACE, place.save());
                view.sendAction(PlacedItems.ACTION_ARRANGE, data);
                return;
            }
        }
        // Full, or nothing to do: not looked at every frame.
        waiting = ANSWER_SECONDS;
        asked = firstLost(there);
    }

    private static int firstLost(PlacedItems there) {
        for (int i = 0; i < there.count(); i++) {
            if (isLost(there, i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isLost(PlacedItems there, int index) {
        return !there.item(index).isEmpty() && there.pose(index) == null;
    }

    /** Where a thing comes down when it is let go of there: as low as it can lie, smaller when it is too high for it else; null when full. */
    @Nullable
    private ItemPose findPlace(PlacedItems there, ItemStack stack) {
        List<Box> boxes = new ArrayList<>();
        for (int i = 0; i < there.count(); i++) {
            ItemPose pose = there.pose(i);
            if (pose != null && !there.item(i).isEmpty()) {
                Vector3f half = halfSize(there.item(i), pose.rotation(), pose.scale());
                boxes.add(new Box(pose.x(), pose.z(), half.x, half.z, pose.top()));
            }
        }
        Quaternionf turn = new Quaternionf().rotateY(boxes.size() % 2 == 0 ? askew : -askew);
        if (isFlat(stack)) {
            turn.rotateX(Mth.HALF_PI);
        }
        for (float scale = 1; scale > SMALLEST - SMALLER / 2; scale -= SMALLER) {
            Vector3f half = halfSize(stack, turn, scale);
            Vector3f place = lowest(boxes, half);
            if (place.y + 2 * half.y <= area.ceiling) {
                return new ItemPose(place.x, place.y, place.z, half.y, turn, scale, -1, 1);
            }
        }
        return null;
    }

    /** From the back to the front, and from one side to the other. */
    private Vector3f lowest(List<Box> boxes, Vector3f half) {
        float middleX = (area.fromX + area.toX) / 2;
        float middleZ = (area.fromZ + area.toZ) / 2;
        float roomX = Math.max(0, (area.toX - area.fromX) / 2 - half.x);
        float roomZ = Math.max(0, (area.toZ - area.fromZ) / 2 - half.z);
        int stepsX = Mth.ceil(2 * roomX / STEP);
        int stepsZ = Mth.ceil(2 * roomZ / STEP);
        Vector3f best = null;
        for (int stepZ = 0; stepZ <= stepsZ; stepZ++) {
            float z = middleZ + roomZ - Math.min(2 * roomZ, stepZ * STEP);
            for (int stepX = 0; stepX <= stepsX; stepX++) {
                float x = middleX + roomX - Math.min(2 * roomX, stepX * STEP);
                float bottom = area.floor;
                for (Box box : boxes) {
                    if (box.isUnder(x, z, half)) {
                        bottom = Math.max(bottom, box.top + DROP);
                    }
                }
                if (best == null || bottom < best.y - 0.01F) {
                    best = new Vector3f(x, bottom, z);
                    if (bottom == area.floor) {
                        return best;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isFlat(ItemStack stack) {
        Vector3f size = PeelRenderer.sizeOf(stack, 1);
        return Math.min(size.x, Math.min(size.y, size.z)) < FLAT;
    }

    /** Half of what a thing takes up when it is turned so. */
    private Vector3f halfSize(ItemStack stack, Quaternionfc turn, float scale) {
        Vector3f half = PeelRenderer.sizeOf(stack, itemSize * scale / 2);
        Matrix3f turned = new Matrix3f().rotation(turn);
        return new Vector3f(Math.abs(turned.m00) * half.x + Math.abs(turned.m10) * half.y + Math.abs(turned.m20) * half.z,
                Math.abs(turned.m01) * half.x + Math.abs(turned.m11) * half.y + Math.abs(turned.m21) * half.z,
                Math.abs(turned.m02) * half.x + Math.abs(turned.m12) * half.y + Math.abs(turned.m22) * half.z);
    }

    /** What a thing that lies there takes up. */
    private record Box(float x, float z, float halfX, float halfZ, float top) {
        boolean isUnder(float otherX, float otherZ, Vector3f half) {
            return Math.abs(otherX - x) < half.x + halfX - TOUCHING && Math.abs(otherZ - z) < half.z + halfZ - TOUCHING;
        }
    }

    private static boolean hasFreeSlot(PlacedItems there) {
        for (int i = 0; i < there.count(); i++) {
            if (there.item(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Whether all of a stack goes onto the same that is there already. */
    private static boolean fitsOnto(PlacedItems there, ItemStack stack) {
        int room = 0;
        for (int i = 0; i < there.count(); i++) {
            if (ItemStack.isSameItemSameTags(there.item(i), stack)) {
                room += stack.getMaxStackSize() - there.item(i).getCount();
            }
        }
        return room >= stack.getCount();
    }
}
