package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.StationState;
import com.renyigesai.bakeries.block.fermentation_box.FermentationBoxBlockEntity;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.fermentation.FermentationStation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FermentationClient implements StationBehavior {
    private static final WorkBlockButtons BUTTONS = new WorkBlockButtons();
    private static final float NEEDLE_SWING = 35 * Mth.DEG_TO_RAD;
    private final BakingTimer timer = new BakingTimer();

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(FermentationStation.ID, view -> new FermentationClient()));
    }

    @Override
    public void onOpen(StationView view) {
        view.setInventoryFilter(stack -> FermentationStation.canFerment(view.level(), stack));
    }

    @Override
    public void onSceneReady(StationView view) {
        setupDurationButton(view, FermentationStation.DECREASE, "-");
        setupDurationButton(view, FermentationStation.INCREASE, "+");
        view.node("power_label").ifPresent(node -> node.setPainter((pose, buffers, light) ->
                OvenPanel.drawLabel(pose, buffers, "⏻", 0.25F, 0)));
        updatePowerState(view);
        updateGaugeTooltip(view);
        timer.setup(view);
        updateTimer(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        var controls = changes.controls();
        if (controls.contains(FermentationStation.POWERED)) {
            updatePowerState(view);
        }
        if (controls.contains(FermentationStation.TEMPERATURE) || controls.contains(FermentationStation.DURATION)
                || controls.contains(FermentationStation.PERFECT_TIME)) {
            updateGaugeTooltip(view);
        }
        if (controls.contains(FermentationStation.REMAINING)) {
            updateTimer(view);
        }
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.ButtonPressed press && FermentationStation.isDurationButton(press.control())) {
            animateDurationPress(view, press.control());
        }
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ((button != 0 && button != 1) || !FermentationStation.isDurationButton(node)) {
            return false;
        }
        // Diegesis routes clicks with a carried stack here instead of to its button control.
        animateDurationPress(view, node);
        view.playSound(SoundEvents.UI_BUTTON_CLICK.value(), node, 0.55F, 1);
        view.sendAction(node, new CompoundTag());
        return true;
    }

    public static float buttonTravel(FermentationBoxBlockEntity box, String control) {
        return BUTTONS.travel(box, control);
    }

    public static float needleAngle(FermentationBoxBlockEntity box) {
        float position = Mth.inverseLerp((float) box.getFermentationMaxTime(), FermentationStation.MIN_DURATION,
                FermentationStation.MAX_DURATION);
        return Mth.clampedLerp(-NEEDLE_SWING, NEEDLE_SWING, position);
    }

    private static void setupDurationButton(StationView view, String id, String label) {
        view.node(id).ifPresent(node -> node.setTooltip(
                Component.translatable("immersive_bakery.fermentation_box." + id, FermentationStation.DURATION_STEP)));
        view.node(id + "_label").ifPresent(node -> node.setPainter((pose, buffers, light) ->
                OvenPanel.drawLabel(pose, buffers, label, 0.22F, 0)));
    }

    private static void animateDurationPress(StationView view, String control) {
        if (view.level().getBlockEntity(view.pos()) instanceof FermentationBoxBlockEntity box) {
            BUTTONS.press(box, control);
        }
    }

    private static void updatePowerState(StationView view) {
        boolean powered = view.state().getControl(FermentationStation.POWERED) > 0;
        for (int slot = 0; slot < FermentationStation.SLOT_COUNT; slot++) {
            view.node("rack_" + slot).ifPresent(node -> node.setEnabled(!powered));
        }
        view.node(FermentationStation.POWER).ifPresent(node -> {
            node.setTint(powered ? 0xFF67C99D : 0xFFBF664F);
            node.setTooltip(Component.translatable(powered
                    ? "immersive_bakery.fermentation_box.power_off" : "immersive_bakery.fermentation_box.power_on"));
        });
    }

    private static void updateGaugeTooltip(StationView view) {
        int perfect = control(view, FermentationStation.PERFECT_TIME);
        // Same wording and suggested range as Bakeries' FermentationBoxScreen.
        view.node("gauge").ifPresent(node -> node.setTooltip(Component.empty()
                .append(Component.translatable("gui.bakeries.today_temperature",
                        control(view, FermentationStation.TEMPERATURE) + "°C"))
                .append("\n" + control(view, FermentationStation.DURATION) + " tick\n")
                .append(Component.translatable("gui.bakeries.suggested_time",
                        Math.max(perfect - 200, FermentationStation.MIN_DURATION), perfect + 200))));
    }

    private void updateTimer(StationView view) {
        float remaining = view.state().getControl(FermentationStation.REMAINING);
        timer.update(view, remaining > 0, remaining);
    }

    private static int control(StationView view, String id) {
        return Math.round(view.state().getControl(id));
    }
}
