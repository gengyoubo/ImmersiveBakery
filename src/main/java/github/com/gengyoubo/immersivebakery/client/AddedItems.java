package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** What was put into something with a mouth, seen going in one after the other: solids drop, vessels tip and pour. */
final class AddedItems {
    private static final String ITEM = "added";
    private static final String STREAM = "stream";
    private static final float SECONDS = 0.9F;
    private static final float TIP_ANGLE = 110;
    /** When the stream runs, in shares of the time, and how long it falls. */
    private static final float STREAM_FROM = 0.28F;
    private static final float STREAM_FALL = 0.12F;
    private static final float STREAM_UNTIL = 0.75F;
    private static final float DROPPED = 0.8F;

    private final Place place;
    private final Predicate<ItemStack> isVessel;
    private final ToIntFunction<ItemStack> color;
    /** The first one is under way. */
    private final Deque<ItemStack> added = new ArrayDeque<>();
    private float time;

    /**
     * Pixels.
     *
     * @param dropFrom, dropTo what drops, over the node
     * @param tipBeside, tipOver a vessel, beside and over the node
     * @param streamTop, streamBottom in the scene
     */
    record Place(float itemSize, float dropFrom, float dropTo, float tipBeside, float tipOver, float streamTop, float streamBottom) {
    }

    AddedItems(Place place, Predicate<ItemStack> isVessel, ToIntFunction<ItemStack> color) {
        this.place = place;
        this.isVessel = isVessel;
        this.color = color;
    }

    void setup(StationView view) {
        added.clear();
        time = 0;
        // As it is, with its potion and all.
        view.node(ITEM).ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            if (!added.isEmpty()) {
                pose.pushPose();
                pose.scale(place.itemSize, place.itemSize, place.itemSize);
                Minecraft.getInstance().getItemRenderer().renderStatic(added.peek(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                        pose, buffers, view.level(), 0);
                pose.popPose();
            }
        }));
        view.node(STREAM).ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            float at = time / SECONDS;
            if (!added.isEmpty() && isVessel.test(added.peek()) && at > STREAM_FROM) {
                float fallen = place.streamTop - place.streamBottom;
                float top = place.streamTop - Math.max(0, at - STREAM_UNTIL) / STREAM_FALL * fallen;
                float bottom = Math.max(place.streamBottom, place.streamTop - (at - STREAM_FROM) / STREAM_FALL * fallen);
                if (top > bottom) {
                    BatterPainter.drawStream(pose, buffers, light, top, bottom, color.applyAsInt(added.peek()));
                }
            }
        }));
    }

    void add(ItemStack stack) {
        added.add(stack);
    }

    /** All that is there without what is not seen in yet: solids before they reached the mouth, vessels before they pour. */
    List<ItemStack> arrived(List<ItemStack> all) {
        List<ItemStack> left = new ArrayList<>(all);
        boolean first = true;
        for (ItemStack stack : added) {
            boolean arrived = first && time > (isVessel.test(stack) ? STREAM_FROM + STREAM_FALL : DROPPED) * SECONDS;
            first = false;
            for (int i = left.size() - 1; i >= 0 && !arrived; i--) {
                if (ItemStack.isSameItemSameTags(left.get(i), stack)) {
                    left.remove(i);
                    break;
                }
            }
        }
        return left;
    }

    boolean isEmpty() {
        return added.isEmpty();
    }

    void frame(StationView view, float deltaSeconds) {
        NodeView node = view.node(ITEM).orElse(null);
        if (node == null) {
            return;
        }
        if (added.isEmpty() || !view.isInUse()) {
            added.clear();
            time = 0;
            node.setVisible(false);
            return;
        }
        time += deltaSeconds;
        float at = Math.min(1, time / SECONDS);
        if (isVessel.test(added.peek())) {
            float tip = Mth.clamp(Math.min(at, 1 - at) * 4, 0, 1);
            node.moveTo(place.tipBeside, place.tipOver, 0, 0);
            node.rotateTo(0, 0, TIP_ANGLE * tip, 0);
        } else {
            node.moveTo(0, Mth.lerp(at * at, place.dropFrom, place.dropTo), 0, 0);
            node.rotateTo(0, 0, 0, 0);
        }
        node.setVisible(true);
        if (at >= 1) {
            added.poll();
            time = 0;
        }
    }
}
