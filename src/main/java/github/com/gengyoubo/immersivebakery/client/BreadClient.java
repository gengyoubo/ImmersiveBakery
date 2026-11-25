package github.com.gengyoubo.immersivebakery.client;

import com.mojang.math.Axis;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.bread.BreadCutting;
import github.com.gengyoubo.immersivebakery.bread.BreadStation;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** The knife that goes round in the mould, and along the lines on the bread. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BreadClient implements StationBehavior {
    private static final String LANG = "immersive_bakery.bread.";
    private static final SlotRef KNIFE = SlotRef.of(BreadStation.KNIFE, 0);
    /** The camera goes round in so many steps; the scene has a glance for each. */
    private static final int TURNS = 8;
    private static final float TURN_SECONDS = 0.35F;
    private static final String TURN_LEFT = "turn_left";
    private static final String TURN_RIGHT = "turn_right";
    private static final float CAPTION_SECONDS = 4;
    /** The knife, pixels: how large, how far its point is from its middle, and where along its blade it cuts bread from above. */
    private static final float KNIFE_SIZE = 8;
    private static final float POINT = 4.5F;
    private static final float ON_BLADE = 1.8F;
    /** How deep it goes. */
    private static final float INTO_MOULD = 3;
    private static final float INTO_BREAD = 1.6F;
    private static final float INTO_SIDE = 5;
    /** Its icon points up to the right. */
    private static final float ICON_LEAN = -45;
    private static final float SEAM_LEAN = -72;
    private static final float LINE_LEAN = -25;
    private static final float POSE_SPEED = 14;
    private static final float SNAP = 1.3F;
    private static final float SNAP_SPEED = 25;
    private static final float CUT_SOUND = 2.2F;
    private static final float ANSWER_SECONDS = 1;

    private BreadPaths paths = new BreadPaths();
    @Nullable
    private BlockState shown;
    private int shownCut;
    @Nullable
    private BreadPaths.Pointer last;
    private final Vector3f lastHand = new Vector3f();
    /** How the drawn knife is off the hand, towards the line. */
    private final Vector3f snapped = new Vector3f();
    private final KnifePose knifePose = new KnifePose(new Quaternionf(), 0, new Vector3f());
    /** How far round the camera is, steps; and how long it is still on its way. */
    private int turn;
    private float turning;
    private float sinceSound;
    private float waiting;
    private String hint = "";

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(BreadStation.ID, view -> new BreadClient()));
    }

    @Override
    public void onOpen(StationView view) {
        view.setInventoryFilter(BreadCutting::isKnife);
        hint = "";
        turn = 0;
        refresh(view);
    }

    /** The wheel, the arrow keys and the arrows beside the bread go round it. */
    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        turn(view, amount > 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        int by = keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A ? 1 : keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D ? -1 : 0;
        if (by != 0) {
            turn(view, by);
        }
        return by != 0;
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && (TURN_LEFT.equals(click.node()) || TURN_RIGHT.equals(click.node()))) {
            turn(view, TURN_LEFT.equals(click.node()) ? 1 : -1);
        }
    }

    private void turn(StationView view, int by) {
        turn = Math.floorMod(turn + by, TURNS);
        turning = TURN_SECONDS;
        view.node("turner").ifPresent(node -> node.rotateTo(0, degrees(), 0, 0));
        view.playSound(SoundEvents.WOOL_STEP, "bread", 0.2F, 1.2F);
        tick(view);
    }

    private float degrees() {
        return 360.0F / TURNS * turn;
    }

    @Override
    public void onSceneReady(StationView view) {
        shown = null;
        turner(view, TURN_LEFT, "↶");
        turner(view, TURN_RIGHT, "↷");
        view.node("bread").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            if (view.isFocused() && !view.state().getItem(KNIFE).isEmpty()) {
                paths.draw(pose, buffers, light);
            }
        }));
        view.node("knife").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            ItemStack knife = view.state().getItem(KNIFE);
            if (knife.isEmpty()) {
                return;
            }
            pose.pushPose();
            if (node.isHeld()) {
                pose.translate(snapped.x + knifePose.into.x, snapped.y + knifePose.into.y, snapped.z + knifePose.into.z);
                pose.mulPose(knifePose.turn);
                pose.translate(-knifePose.at, 0, 0);
                pose.mulPose(Axis.ZP.rotationDegrees(ICON_LEAN));
                pose.scale(KNIFE_SIZE, KNIFE_SIZE, KNIFE_SIZE);
            } else {
                // Lies before the bread, its point to the left.
                pose.translate(0, KNIFE_SIZE / 32, 0);
                pose.mulPose(Axis.XP.rotationDegrees(90));
                pose.mulPose(Axis.ZP.rotationDegrees(-45));
                pose.scale(KNIFE_SIZE, KNIFE_SIZE, KNIFE_SIZE);
            }
            Minecraft.getInstance().getItemRenderer().renderStatic(knife, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, view.level(), 0);
            pose.popPose();
        }));
        refresh(view);
    }

    private static void turner(StationView view, String name, String arrow) {
        view.node(name).ifPresent(node -> {
            node.setTooltip(Component.translatable(LANG + name));
            node.setPainter((pose, buffers, light) -> {
                if (view.isFocused()) {
                    pose.pushPose();
                    // What is drawn faces up.
                    pose.mulPose(Axis.XP.rotationDegrees(90));
                    OvenPanel.drawLabel(pose, buffers, arrow, 0.5F, 0);
                    pose.popPose();
                }
            });
        });
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    @Override
    public void tick(StationView view) {
        if (waiting > 0 && (waiting -= 0.05F) <= 0) {
            // The server made nothing of it.
            shown = null;
        }
        if (view.level().getBlockState(view.pos()) != shown) {
            refresh(view);
        }
        boolean large = shown != null && BreadCutting.isLarge(shown);
        String wanted = large ? "cake_" + turn : turn == 0 ? "" : "bread_" + turn;
        if (view.isFocused() && !wanted.equals(view.currentGlance().orElse(""))) {
            turning = TURN_SECONDS;
            if (wanted.isEmpty()) {
                view.glanceBack();
            } else {
                view.glance(wanted);
            }
        }
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        turning = Math.max(0, turning - deltaSeconds);
        NodeView knife = view.node("knife").orElse(null);
        if (knife == null || !knife.isHeld()) {
            last = null;
            snapped.zero();
            knifePose.follow(KnifePose.of(null, degrees()), 1);
            return;
        }
        Vector3f at = knife.scenePosition();
        BreadPaths.Pointer pointer = paths.pointer(height -> view.pointerOn("bread", height));
        BreadPaths.Place onto = paths.nearest(pointer, SNAP);
        Vector3f off = onto == null ? new Vector3f() : new Vector3f(onto.point()).sub(at);
        snapped.lerp(off, Math.min(1, deltaSeconds * SNAP_SPEED));
        knifePose.follow(KnifePose.of(onto, degrees()), Math.min(1, deltaSeconds * POSE_SPEED));
        if (view.isFocused() && onto != null && waiting <= 0 && last != null && turning <= 0) {
            List<BreadPaths.Path> done = new ArrayList<>();
            float way = at.distance(lastHand);
            if (paths.cut(last, pointer, way, done)) {
                sinceSound += way;
                if (sinceSound > CUT_SOUND) {
                    sinceSound = 0;
                    boolean mould = shown != null && BreadCutting.isInMould(shown);
                    view.playSound(mould ? SoundEvents.CHAIN_STEP : SoundEvents.WOOL_STEP, "bread", 0.4F, 1.4F);
                }
            }
            for (BreadPaths.Path path : done) {
                waiting = path.line == 0 ? ANSWER_SECONDS : 0;
                CompoundTag data = new CompoundTag();
                data.putInt(BreadStation.LINE, path.line);
                view.sendAction(path.line == 0 ? BreadStation.ACTION_LOOSEN : BreadStation.ACTION_CUT, data);
            }
        }
        // The camera moves the cursor over the bread, not the hand.
        last = turning > 0 ? null : pointer;
        lastHand.set(at);
    }

    /** How the knife is held: how it is turned, pointing along x before; the place along it that is on the line, and how far that goes in. */
    private static final class KnifePose {
        private final Quaternionf turn;
        private final Vector3f into;
        private float at;

        private KnifePose(Quaternionf turn, float at, Vector3f into) {
            this.turn = turn;
            this.at = at;
            this.into = into;
        }

        /** For a camera that is so many degrees round: the handle points to the player. */
        private static KnifePose of(@Nullable BreadPaths.Place place, float degrees) {
            if (place == null) {
                return new KnifePose(Axis.YP.rotationDegrees(degrees - 62).mul(Axis.ZP.rotationDegrees(-33)), POINT, new Vector3f());
            }
            return switch (place.cut()) {
                case SEAM_ALONG_X -> along(1, 0, degrees, SEAM_LEAN, POINT, INTO_MOULD);
                case SEAM_ALONG_Z -> along(0, 1, degrees, SEAM_LEAN, POINT, INTO_MOULD);
                case LINE_ALONG_X -> along(1, 0, degrees, LINE_LEAN, ON_BLADE, INTO_BREAD);
                case LINE_ALONG_Z -> along(0, 1, degrees, LINE_LEAN, ON_BLADE, INTO_BREAD);
                case LAYER -> {
                    float x = -place.side().getStepX();
                    float z = -place.side().getStepZ();
                    yield new KnifePose(pointing(x, z).mul(Axis.XP.rotationDegrees(90)), POINT, new Vector3f(x, 0, z).mul(INTO_SIDE));
                }
            };
        }

        /** Along a line its point is away from the player; across, to the left. */
        private static KnifePose along(float x, float z, float degrees, float lean, float at, float into) {
            float radians = degrees * Mth.DEG_TO_RAD;
            float away = x * Mth.sin(radians) + z * Mth.cos(radians);
            float left = x * Mth.cos(radians) - z * Mth.sin(radians);
            float sign = away + 0.4F * left < 0 ? -1 : 1;
            return new KnifePose(pointing(sign * x, sign * z).mul(Axis.ZP.rotationDegrees(lean)), at, new Vector3f(0, -into, 0));
        }

        private static Quaternionf pointing(float x, float z) {
            return Axis.YP.rotation((float) Mth.atan2(-z, x));
        }

        private void follow(KnifePose to, float amount) {
            turn.slerp(to.turn, amount);
            into.lerp(to.into, amount);
            at += (to.at - at) * amount;
        }
    }

    private void refresh(StationView view) {
        BlockState bread = view.level().getBlockState(view.pos());
        int cut = view.state().data().getInt(BreadStation.CUT);
        if (bread != shown || cut != shownCut) {
            // A line the hand is on goes on where it is, unless another one is answered meanwhile.
            boolean answered = bread == shown && (cut & ~shownCut) != 0;
            if (!answered) {
                paths = BreadPaths.of(bread, cut);
                last = null;
                waiting = 0;
            }
            shown = bread;
            shownCut = cut;
        }
        boolean hasKnife = !view.state().getItem(KNIFE).isEmpty();
        view.node("knife").ifPresent(node -> node.setEnabled(hasKnife));
        view.node("rest").ifPresent(node -> node.setEnabled(!hasKnife));
        Component told = !hasKnife ? Component.translatable(LANG + "no_knife")
                : BreadCutting.isInMould(bread) ? Component.translatable(LANG + "loosen")
                : BreadCutting.isLayered(bread) ? Component.translatable(LANG + "layers", BreadCutting.slices(bread))
                : Component.translatable(LANG + "cut", BreadCutting.slices(bread));
        view.node("knife").ifPresent(node -> node.setTooltip(told));
        view.node("rest").ifPresent(node -> node.setTooltip(told));
        if (view.isFocused() && !told.getString().equals(hint)) {
            hint = told.getString();
            view.showCaption(told, CAPTION_SECONDS);
        }
    }
}
