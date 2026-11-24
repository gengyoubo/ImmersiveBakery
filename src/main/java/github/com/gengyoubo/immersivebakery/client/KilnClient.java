package github.com.gengyoubo.immersivebakery.client;

import com.mojang.math.Axis;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import com.renyigesai.bakeries.block.stone_kiln.StoneKilnBlockEntity;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.kiln.KilnStation;
import github.com.gengyoubo.immersivebakery.kiln.Peel;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KilnClient implements StationBehavior {
    private static final String LANG = "immersive_bakery.stone_kiln.";
    private static final SlotRef PEEL = SlotRef.of(KilnStation.PEEL, 0);
    private static final SlotRef LOAD = SlotRef.of(KilnStation.LOAD, 0);
    private static final SlotRef HEARTH = SlotRef.of(KilnStation.HEARTH, 0);
    private static final float CAPTION_SECONDS = 4;
    /** The middle of the kiln, and how far in the blade is when it gets under the bread, stays there and comes out from under it, scene pixels. */
    private static final float MIDDLE = 8;
    private static final float INSERTED_Z = 5;
    private static final float UNDER_BREAD_Z = 6;
    private static final float PULLED_OUT_Z = 2;
    /** Swinging, a hand goes round: it may come this far out before the peel does. */
    private static final float PULLED_OUT_SWINGING_Z = -3;
    /** How the handle swings with the hand, as far as the mouth of the kiln allows, how much of it is a stroke, and from when it is clear to which side. */
    private static final float DEGREES_PER_PIXEL = 7;
    private static final float MOST_DEGREES = 26;
    private static final float STROKE_DEGREES = 24;
    private static final float SIDE_DEGREES = 3;
    private static final float CATCH_UP = 0.15F;
    private static final float SLIDE_UNDER_SECONDS = 0.12F;
    private static final Set<StoneKilnBlockEntity> TURNED_BY_HAND = Collections.newSetFromMap(new WeakHashMap<>());
    private String hint = "";
    @Nullable
    private Turning turning;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(KilnStation.ID, view -> new KilnClient()));
    }

    @Override
    public void onOpen(StationView view) {
        view.setInventoryFilter(stack -> Peel.isPeel(stack) || Peel.isRaw(view.level(), stack));
        hint = "";
        refresh(view);
    }

    @Override
    public void onSceneReady(StationView view) {
        view.node("peel").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            ItemStack peel = view.state().getItem(PEEL);
            if (!peel.isEmpty()) {
                // The blade's upper face is the node, the handle points to the player.
                pose.pushPose();
                if (turning != null && turning.under > 0) {
                    // A carried node goes where the hand goes; drawn, its blade stays under the bread.
                    Vector3f hand = node.scenePosition();
                    pose.translate((MIDDLE - hand.x) * turning.under, 0, Math.max(0, UNDER_BREAD_Z - hand.z) * turning.kept * turning.under);
                    pose.mulPose(Axis.YP.rotationDegrees(-swing(hand) * turning.under));
                }
                pose.scale(16, 16, 16);
                pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.mulPose(Axis.XP.rotationDegrees(-90));
                pose.translate(0.5F - PeelRenderer.BLADE_X / 16, 0.5F - PeelRenderer.BLADE_Y / 16, 0.5F - PeelRenderer.BLADE_Z / 16);
                Minecraft.getInstance().getItemRenderer().renderStatic(peel, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                        pose, buffers, view.level(), 0);
                pose.popPose();
            }
        }));
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Contact contact && contact.touching() && "peel".equals(contact.node()) && "hearth".equals(contact.other())
                && view.state().getControl(KilnStation.WANTS_TURN) == 0) {
            view.sendAction(KilnStation.ACTION_REACH, new CompoundTag());
        }
    }

    @Override
    public void onClose(StationView view) {
        if (turning != null && !turning.done && view.level().getBlockEntity(view.pos()) instanceof StoneKilnBlockEntity kiln) {
            // Half a turn is none: the bread lies as it did.
            kiln.progress -= turning.turned;
            kiln.progressOld = kiln.progress;
        }
        turning = null;
    }

    /**
     * A turn takes two strokes to the same side, as a long peel has no room for more at once: in at the middle and over to one
     * side, out, in again at the far other side and back to the middle.
     */
    @Override
    public void frame(StationView view, float deltaSeconds) {
        NodeView peel = view.node("peel").orElse(null);
        if (peel == null || !(view.level().getBlockEntity(view.pos()) instanceof StoneKilnBlockEntity kiln)) {
            return;
        }
        // Where the peel is tells whether it is in, not what led there: it may wait in the kiln for the turn or leave the hand in there.
        Vector3f hand = peel.scenePosition();
        boolean wantsTurn = view.state().getControl(KilnStation.WANTS_TURN) > 0;
        boolean free = wantsTurn && peel.isHeld() && view.state().getItem(LOAD).isEmpty();
        if (turning != null && turning.done) {
            turning.settled |= !wantsTurn;
        }
        if (free && hand.z > INSERTED_Z) {
            if (turning == null || turning.settled) {
                turning = new Turning(turning);
                turning.insert();
                refresh(view);
            } else if (!turning.inserted && !turning.done) {
                turning.insert();
            }
        }
        if (turning == null) {
            return;
        }
        if (turning.inserted && (!peel.isHeld() || hand.z < (turning.isSwinging() ? PULLED_OUT_SWINGING_Z : PULLED_OUT_Z))) {
            turning.pullOut();
            refresh(view);
        }
        float slide = deltaSeconds / SLIDE_UNDER_SECONDS;
        turning.under = Mth.clamp(turning.under + (turning.inserted ? slide : -slide), 0, 1);
        turning.kept = Mth.clamp(turning.kept + (turning.inserted && !turning.strokeDone ? slide : -slide), 0, 1);
        if (turning.done) {
            if (!turning.inserted && turning.under == 0) {
                turning = null;
            }
            return;
        }
        if (!turning.inserted) {
            return;
        }
        float before = turning.turned;
        boolean wasOver = turning.strokeDone;
        turning.swing(swing(hand));
        kiln.setAnimationStatus(StoneKilnBlockEntity.AnimationStatus.STOP);
        kiln.progress += turning.turned - before;
        kiln.progressOld = kiln.progress;
        if (turning.strokeDone && !wasOver) {
            refresh(view);
        }
        if (turning.done) {
            TURNED_BY_HAND.add(kiln);
            view.sendAction(KilnStation.ACTION_TURN, new CompoundTag());
        }
    }

    private static float swing(Vector3f hand) {
        return Mth.clamp((hand.x - MIDDLE) * DEGREES_PER_PIXEL, -MOST_DEGREES, MOST_DEGREES);
    }

    /** Whether the turn Bakeries announces was seen in the hand already and needs no animation. */
    public static boolean wasTurnedByHand(StoneKilnBlockEntity kiln) {
        return TURNED_BY_HAND.remove(kiln);
    }

    /** A bread being turned. Angles are the handle's, to the side it is swung to. */
    private static final class Turning {
        private boolean inserted;
        /** How far the blade has slid under the bread, and how firmly it is kept there however far out the hand is, 0 to 1. */
        private float under;
        private float kept;
        private int side;
        private float entered = Float.NaN;
        private float start;
        private float stroke;
        /** Whether the hand, in again in the middle of a stroke, is where it left the bread. */
        private boolean caughtUp;
        private boolean secondStroke;
        private boolean strokeDone;
        private boolean done;
        /** Whether the kiln knows it is done, and the next turn may start. */
        private boolean settled;
        /** How far Bakeries' animation, which counts half turns, is moved on by now. */
        private float turned;

        private Turning(@Nullable Turning last) {
            if (last != null) {
                under = last.under;
                kept = last.kept;
            }
        }

        private void insert() {
            inserted = true;
            entered = Float.NaN;
            caughtUp = false;
        }

        private boolean isSwinging() {
            return stroke > 0 && !strokeDone;
        }

        private void pullOut() {
            inserted = false;
            if (strokeDone && !done) {
                secondStroke = true;
                strokeDone = false;
                stroke = 0;
            }
        }

        private void swing(float angle) {
            if (Float.isNaN(entered)) {
                entered = angle;
                if (stroke == 0) {
                    start = angle * side;
                }
            }
            if (side == 0) {
                if (Math.abs(angle - entered) < SIDE_DEGREES) {
                    return;
                }
                side = angle > entered ? 1 : -1;
                start = entered * side;
            }
            float at = angle * side;
            if (stroke == 0) {
                // Swung back, the peel slides under the bread and leaves it as it lies.
                start = Math.min(start, at);
            }
            float from = secondStroke ? start : Math.max(0, start);
            float to = secondStroke ? 0 : STROKE_DEGREES;
            boolean entersWell = secondStroke ? from <= -STROKE_DEGREES / 2 : from <= STROKE_DEGREES / 2;
            if (strokeDone || !entersWell) {
                return;
            }
            float further = Mth.clamp((at - from) / (to - from), 0, 1) - stroke;
            caughtUp |= further < CATCH_UP;
            if (caughtUp && further > 0) {
                stroke += further;
                turned -= side * further / 2;
                strokeDone = stroke >= 0.98F;
                done = strokeDone && secondStroke;
            }
        }
    }

    private void refresh(StationView view) {
        StationState state = view.state();
        boolean hasPeel = !state.getItem(PEEL).isEmpty();
        boolean loaded = !state.getItem(LOAD).isEmpty();
        ItemStack baking = state.getItem(HEARTH);
        boolean wantsTurn = state.getControl(KilnStation.WANTS_TURN) > 0;
        boolean done = state.getControl(KilnStation.DONE) > 0;
        view.node("peel").ifPresent(node -> node.setEnabled(hasPeel));
        view.node("rest").ifPresent(node -> node.setEnabled(!hasPeel));
        view.node("blade").ifPresent(node -> node.setEnabled(hasPeel && !loaded));
        view.node("load").ifPresent(node -> node.setEnabled(hasPeel && loaded));

        String next = !hasPeel ? "no_peel"
                : done ? loaded ? "unload" : "take"
                : wantsTurn ? loaded ? "unload" : turning == null || turning.side == 0 || !turning.secondStroke && !turning.strokeDone ? "turn"
                        : turning.strokeDone ? "turn_out" : turning.side > 0 ? "turn_from_right" : "turn_from_left"
                : !baking.isEmpty() ? state.getControl(KilnStation.LIT) > 0 ? "baking" : "unlit"
                : loaded ? Peel.isRaw(view.level(), state.getItem(LOAD)) ? "put" : "unload" : "insert";
        view.node("peel").ifPresent(node -> node.setTooltip(Component.translatable(LANG + next)));
        view.node("rest").ifPresent(node -> node.setTooltip(Component.translatable(LANG + "no_peel")));
        if (view.isFocused() && !next.equals(hint)) {
            hint = next;
            view.showCaption(Component.translatable(LANG + next), CAPTION_SECONDS);
        }
    }
}
