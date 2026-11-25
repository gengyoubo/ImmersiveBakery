package github.com.gengyoubo.immersivebakery.storage;

import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.StationState;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;

/**
 * The two drawers of a cupboard, and of the table that dough is shaped on. They are pulled out on the side that is used. What is
 * in both of them is in one container: first that of the upper one, then that of the lower one.
 */
public final class Drawers {
    public static final String ITEMS = "items";
    public static final int COUNT = 2;
    public static final int SLOTS_EACH = 15;
    public static final int SLOT_COUNT = COUNT * SLOTS_EACH;
    /** Whether each is pulled out, the upper first. */
    private static final String[] OPEN = {"open", "open_lower"};
    /** On which side: so many sides clockwise from the front. */
    private static final String SIDE = "side";
    public static final int SIDES = 4;
    /** A drawer is pulled out or pushed in; data: {@link #DRAWER}, which. */
    public static final String ACTION_DRAWER = "drawer";
    public static final String DRAWER = "drawer";
    /** Which side they are pulled out on from now on; data: {@link #ON_SIDE}. */
    public static final String ACTION_SIDE = "side";
    public static final String ON_SIDE = "on_side";
    /** Their floors, and how high things may lie on those, scene pixels. */
    public static final float[] FLOOR = {9, 3};
    public static final float HEIGHT = 4;

    private Drawers() {
    }

    public static StationSchema.Builder addTo(StationSchema.Builder schema) {
        schema.slots(ITEMS, SLOT_COUNT, SlotRules.DEFAULT.placed().kept());
        for (String open : OPEN) {
            schema.control(open, ControlSpec.toggle(false).locked());
        }
        return schema.control(SIDE, ControlSpec.stepped(0, SIDES - 1, SIDES - 1, 0).locked());
    }

    public static boolean isOpen(StationState state, int drawer) {
        return state.getControl(OPEN[drawer]) > 0;
    }

    static void setOpen(StationState state, int drawer, boolean open) {
        state.setControl(OPEN[drawer], open ? 1 : 0);
    }

    public static boolean isAnyOpen(StationState state) {
        return isOpen(state, 0) || isOpen(state, 1);
    }

    /** Whether one was open. */
    static boolean shut(StationState state) {
        boolean wasOpen = isAnyOpen(state);
        setOpen(state, 0, false);
        setOpen(state, 1, false);
        return wasOpen;
    }

    public static int side(StationState state) {
        return Math.round(state.getControl(SIDE));
    }

    static void setSide(StationState state, int side) {
        if (side >= 0 && side < SIDES && !isAnyOpen(state)) {
            state.setControl(SIDE, side);
        }
    }

    /** What is in one of them. */
    public static PlacedItems items(StationState state, int drawer) {
        return new PlacedItems(state, ITEMS, drawer * SLOTS_EACH, SLOTS_EACH);
    }

    private static int drawerAt(ItemPose pose) {
        return pose.y() > FLOOR[0] - 1 ? 0 : 1;
    }

    static float ceiling(int drawer) {
        return FLOOR[drawer] + HEIGHT;
    }

    /** What was put into one of them takes one of its slots; what finds none left there comes out again. */
    static void sort(StationState state, Consumer<ItemStack> out) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            SlotRef slot = SlotRef.of(ITEMS, i);
            ItemPose pose = state.getPose(slot);
            if (pose == null || state.getItem(slot).isEmpty() || drawerAt(pose) == i / SLOTS_EACH) {
                continue;
            }
            ItemStack stack = state.getItem(slot).copy();
            state.setItem(slot, ItemStack.EMPTY);
            state.setPose(slot, null);
            int free = firstFree(state, drawerAt(pose));
            if (free < 0) {
                out.accept(stack);
                continue;
            }
            state.setPose(SlotRef.of(ITEMS, free), pose);
            state.setItem(SlotRef.of(ITEMS, free), stack);
            for (int above = 0; above < SLOT_COUNT; above++) {
                ItemPose other = state.getPose(SlotRef.of(ITEMS, above));
                if (other != null && other.on() == i) {
                    state.setPose(SlotRef.of(ITEMS, above), other.standingOn(free));
                }
            }
        }
    }

    private static int firstFree(StationState state, int drawer) {
        for (int i = drawer * SLOTS_EACH; i < (drawer + 1) * SLOTS_EACH; i++) {
            if (state.getItem(SlotRef.of(ITEMS, i)).isEmpty()) {
                return i;
            }
        }
        return -1;
    }
}
