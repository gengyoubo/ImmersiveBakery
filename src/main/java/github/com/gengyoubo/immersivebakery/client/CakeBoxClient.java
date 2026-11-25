package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.box.CakeBoxStation;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

/** The cake box: its ribbon is pulled off, its lid lifted by the handle. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CakeBoxClient implements StationBehavior {
    private static final String LANG = "immersive_bakery.cake_box.";
    private static final String RIBBON = "ribbon";
    private static final String LID = "lid";
    private static final String OPEN_LID = "open_lid";
    private static final float CAPTION_SECONDS = 4;
    /** How far the ribbon is pulled until it is untied, and the lid lifted until it is open, pixels. */
    private static final float PULLED = 6;
    private static final float LIFTED = 5;
    private static final float LEVEL = 11;
    /** The inside of the box, blocks. */
    private static final float FLOOR = 0.02F;
    private static final float MOST_WIDE = 10.9F / 16;
    private static final float MOST_HIGH = 10.4F / 16;
    /** The hand does not untie all of it: the rest falls by itself. */
    private static final float BY_HAND = 0.35F;
    private static final float LID_BY_HAND = 0.6F;
    private static final float PER_SECOND = 2.6F;

    private float untied;
    private float open;
    /** What was told the server and is not answered yet. */
    private boolean untying;
    private boolean opening;
    /** Where the hand was when it took a node. */
    private final Map<String, Vector3f> taken = new HashMap<>();
    private String hint = "";

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(CakeBoxStation.ID, view -> new CakeBoxClient()));
    }

    @Override
    public void onOpen(StationView view) {
        view.setInventoryFilter(CakeBoxStation::fits);
        hint = "";
        refresh(view);
    }

    @Override
    public void onSceneReady(StationView view) {
        untied = 0;
        open = 0;
        view.node("box").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            BlockState box = view.level().getBlockState(view.pos());
            if (view.isBlockHidden() && box.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                CakeBoxPainter.draw(box, pose, buffers, light, ease(untied), ease(open));
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
        if (event instanceof SceneEvent.Click click && OPEN_LID.equals(click.node())) {
            view.sendAction(CakeBoxStation.ACTION_CLOSE, new CompoundTag());
        }
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        boolean isUntied = isOn(view, CakeBoxStation.UNTIED);
        boolean isOpen = isOn(view, CakeBoxStation.OPEN);
        float pulled = pulled(view, RIBBON, PULLED, false);
        if (pulled >= 1 && !isUntied && !untying && view.isFocused()) {
            untying = true;
            view.sendAction(CakeBoxStation.ACTION_UNTIE, new CompoundTag());
        }
        float lifted = pulled(view, LID, LIFTED, true);
        if (lifted >= 1 && !isOpen && !opening && view.isFocused()) {
            opening = true;
            view.sendAction(CakeBoxStation.ACTION_OPEN, new CompoundTag());
        }
        untied = towards(untied, isUntied ? 1 : BY_HAND * Math.max(0, pulled), deltaSeconds);
        open = towards(open, isOpen ? 1 : LID_BY_HAND * Math.max(0, lifted), deltaSeconds);
        if (!isUntied) {
            untying &= view.node(RIBBON).map(NodeView::isHeld).orElse(false);
        }
        if (!isOpen) {
            opening &= view.node(LID).map(NodeView::isHeld).orElse(false);
        }
    }

    /** How far the hand that holds a node went since it took it, 0 to 1; below 0 while it is not held. */
    private float pulled(StationView view, String name, float whole, boolean up) {
        boolean held = view.node(name).map(NodeView::isHeld).orElse(false);
        // On the level of the lid, so that up on the screen is away there.
        Vector3f at = held ? view.pointerOn("box", LEVEL).orElse(null) : null;
        if (at == null) {
            taken.remove(name);
            return -1;
        }
        Vector3f from = taken.computeIfAbsent(name, key -> new Vector3f(at));
        return Mth.clamp((up ? at.z - from.z : at.distance(from)) / whole, 0, 1);
    }

    /** What is in the box, in the block's own space, as large as it is where it stands on its own. */
    public static void drawCake(ItemStack cake, Direction facing, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5F, FLOOR, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        PeelRenderer.drawStanding(cake, MOST_WIDE, MOST_HIGH, pose, buffers, light, overlay);
        pose.popPose();
    }

    private static float towards(float now, float wanted, float deltaSeconds) {
        float step = PER_SECOND * deltaSeconds;
        return Mth.clamp(wanted, now - step, now + step);
    }

    private static float ease(float amount) {
        return amount * amount * (3 - 2 * amount);
    }

    private static boolean isOn(StationView view, String control) {
        return view.isInUse() && view.state().getControl(control) > 0;
    }

    private void refresh(StationView view) {
        boolean isUntied = isOn(view, CakeBoxStation.UNTIED);
        boolean isOpen = isOn(view, CakeBoxStation.OPEN);
        boolean empty = view.state().getItem(SlotRef.of(CakeBoxStation.CAKE, 0)).isEmpty();
        Component told = Component.translatable(LANG + (!isUntied ? "untie" : !isOpen ? "open" : empty ? "put" : "take"));
        view.node(RIBBON).ifPresent(node -> {
            node.setEnabled(!isUntied || node.isHeld());
            node.setTooltip(told);
        });
        view.node(LID).ifPresent(node -> {
            node.setEnabled(isUntied && !isOpen || node.isHeld());
            node.setTooltip(told);
        });
        view.node(OPEN_LID).ifPresent(node -> {
            node.setEnabled(isOpen);
            node.setTooltip(Component.translatable(LANG + "close"));
        });
        view.node("cake").ifPresent(node -> node.setEnabled(isOpen));
        if (view.isFocused() && !told.getString().equals(hint)) {
            hint = told.getString();
            view.showCaption(told, CAPTION_SECONDS);
        }
    }
}
