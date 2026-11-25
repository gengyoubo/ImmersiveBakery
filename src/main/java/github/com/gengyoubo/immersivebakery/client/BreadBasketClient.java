package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.basket.BreadBasketStation;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/** The bread basket: whole stacks of the inventory go into it as long as there is room. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BreadBasketClient implements StationBehavior {
    private static final float CAPTION_SECONDS = 2.5F;
    private static final float HINT_SECONDS = 5;
    /** As the scene has them, pixels. */
    private static final float MIDDLE = 8;
    private static final float FLOOR = 1;
    private static final float ITEM_SIZE = 5;
    private static final float HALF_WIDE = 3;
    /** How small a thing is made to fit in, and in which steps. */
    private static final float SMALLEST = 0.4F;
    private static final float SMALLER = 0.1F;
    private static final float STEP = 0.25F;
    private static final float TOUCHING = 0.1F;
    private static final float DROP = 0.3F;
    private static final float ASKEW = 0.12F;
    /** As thin as this, a thing lies; Diegesis has it so. */
    private static final float FLAT = 0.2F;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(BreadBasketStation.ID, view -> new BreadBasketClient()));
    }

    @Override
    public void onOpen(StationView view) {
        view.showCaption(Component.translatable("immersive_bakery.bread_basket.hint"), HINT_SECONDS);
    }

    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CompoundTag data = new CompoundTag();
        if (!fitsOnto(view, stack)) {
            ItemPose place = findPlace(view.state(), stack);
            if (place == null) {
                view.showCaption(Component.translatable("immersive_bakery.bread_basket.full"), CAPTION_SECONDS);
                return true;
            }
            data.put(BreadBasketStation.PLACE, place.save());
        }
        data.putInt(BreadBasketStation.INVENTORY_SLOT, slot);
        view.sendAction(BreadBasketStation.ACTION_STORE, data);
        return true;
    }

    /** Where a thing comes down when it is let go of there: as low as it can lie, smaller when it is too high for it else; null when full. */
    @Nullable
    private static ItemPose findPlace(StationState state, ItemStack stack) {
        List<Box> boxes = new ArrayList<>();
        for (int i = 0; i < BreadBasketStation.SLOT_COUNT; i++) {
            SlotRef slot = SlotRef.of(BreadBasketStation.BREADS, i);
            ItemStack there = state.getItem(slot);
            if (!there.isEmpty()) {
                boxes.add(Box.of(there, state.getPose(slot)));
            }
        }
        Quaternionf turn = new Quaternionf().rotateY(boxes.size() % 2 == 0 ? ASKEW : -ASKEW);
        if (isFlat(stack)) {
            turn.rotateX(Mth.HALF_PI);
        }
        for (float scale = 1; scale >= SMALLEST; scale -= SMALLER) {
            ItemPose place = lowest(boxes, halfSize(stack, turn, scale), turn, scale);
            if (place.top() <= BreadBasketStation.HEAPED) {
                return place;
            }
        }
        return null;
    }

    private static ItemPose lowest(List<Box> boxes, Vector3f half, Quaternionf turn, float scale) {
        float roomX = Math.max(0, HALF_WIDE - half.x);
        float roomZ = Math.max(0, HALF_WIDE - half.z);
        Vector3f best = null;
        for (float x : new float[] {MIDDLE, MIDDLE + roomX, MIDDLE - roomX}) {
            for (float z = MIDDLE + roomZ; z >= MIDDLE - roomZ - 0.01F; z -= Math.max(STEP, roomZ / 8)) {
                float bottom = FLOOR;
                for (Box box : boxes) {
                    if (box.isUnder(x, z, half)) {
                        bottom = Math.max(bottom, box.top + DROP);
                    }
                }
                if (best == null || bottom < best.y - 0.01F) {
                    best = new Vector3f(x, bottom, z);
                }
            }
        }
        return new ItemPose(best.x, best.y, best.z, half.y, turn, scale, -1, 1);
    }

    private static boolean isFlat(ItemStack stack) {
        Vector3f size = PeelRenderer.sizeOf(stack, 1);
        return Math.min(size.x, Math.min(size.y, size.z)) < FLAT;
    }

    /** Half of what a thing takes up when it is turned so. */
    private static Vector3f halfSize(ItemStack stack, Quaternionfc turn, float scale) {
        Vector3f half = PeelRenderer.sizeOf(stack, ITEM_SIZE * scale / 2);
        Matrix3f turned = new Matrix3f().rotation(turn);
        return new Vector3f(Math.abs(turned.m00) * half.x + Math.abs(turned.m10) * half.y + Math.abs(turned.m20) * half.z,
                Math.abs(turned.m01) * half.x + Math.abs(turned.m11) * half.y + Math.abs(turned.m21) * half.z,
                Math.abs(turned.m02) * half.x + Math.abs(turned.m12) * half.y + Math.abs(turned.m22) * half.z);
    }

    /** What a thing that lies there takes up. */
    private record Box(float x, float z, float halfX, float halfZ, float top) {
        static Box of(ItemStack stack, @Nullable ItemPose pose) {
            if (pose == null) {
                // Set out by itself somewhere on the floor.
                Vector3f half = halfSize(stack, isFlat(stack) ? new Quaternionf().rotateX(Mth.HALF_PI) : new Quaternionf(), 1);
                return new Box(MIDDLE, MIDDLE, HALF_WIDE, HALF_WIDE, FLOOR + 2 * half.y);
            }
            Vector3f half = halfSize(stack, pose.rotation(), pose.scale());
            return new Box(pose.x(), pose.z(), half.x, half.z, pose.top());
        }

        boolean isUnder(float otherX, float otherZ, Vector3f half) {
            return Math.abs(otherX - x) < half.x + halfX - TOUCHING && Math.abs(otherZ - z) < half.z + halfZ - TOUCHING;
        }
    }

    /** Whether all of a stack goes onto the same that is there already. */
    private static boolean fitsOnto(StationView view, ItemStack stack) {
        int room = 0;
        for (ItemStack there : view.state().getItems(BreadBasketStation.BREADS)) {
            if (ItemStack.isSameItemSameTags(there, stack)) {
                room += there.getMaxStackSize() - there.getCount();
            }
        }
        return room >= stack.getCount();
    }
}
