package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.StationState;
import com.renyigesai.bakeries.block.oven.OvenBlockEntity;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.oven.OvenStation;
import java.lang.ref.WeakReference;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class OvenClient implements StationBehavior {
    private static WeakReference<OvenBlockEntity> pressedOven = new WeakReference<>(null);
    private static long increasePressedAt;
    private static long decreasePressedAt;
    private final BakingTimer timer = new BakingTimer();

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(OvenStation.ID, view -> new OvenClient()));
    }

    @Override
    public void onOpen(StationView view) {
        view.setInventoryFilter(stack -> OvenStation.canBake(view.level(), stack));
    }

    @Override
    public void onSceneReady(StationView view) {
        setTemperatureTooltip(view, OvenStation.INCREASE);
        setTemperatureTooltip(view, OvenStation.DECREASE);
        view.node("power_label").ifPresent(node -> node.setPainter((pose, buffers, light) ->
                OvenPanel.drawLabel(pose, buffers, "⏻", 0.25F, 0)));
        updatePowerState(view);
        view.node(OvenStation.TEMPERATURE).ifPresent(node -> node.setPainter((pose, buffers, light) ->
                OvenPanel.drawTemperature(pose, buffers, Math.round(view.state().getControl(OvenStation.TEMPERATURE)))));
        timer.setup(view);
        updateTimer(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        if (changes.controls().contains(OvenStation.POWERED)) {
            updatePowerState(view);
        }
        if (changes.controls().contains(OvenStation.BAKING_REMAINING)) {
            updateTimer(view);
        }
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.ButtonPressed press && OvenStation.isTemperatureButton(press.control())) {
            animateTemperaturePress(view, press.control());
        }
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ((button != 0 && button != 1) || !OvenStation.isTemperatureButton(node)) {
            return false;
        }
        // Diegesis routes clicks with a carried stack here instead of to its button control.
        animateTemperaturePress(view, node);
        view.playSound(SoundEvents.UI_BUTTON_CLICK.value(), node, 0.55F, 1);
        view.sendAction(node, new CompoundTag());
        return true;
    }

    public static float buttonTravel(OvenBlockEntity oven, String control) {
        if (pressedOven.get() != oven) {
            return 0;
        }
        long pressedAt = OvenStation.INCREASE.equals(control) ? increasePressedAt : decreasePressedAt;
        if (pressedAt == 0) {
            return 0;
        }
        long elapsed = Util.getMillis() - pressedAt;
        return 0.78F * Math.max(0, 1 - Math.max(0, elapsed - 55) / 120.0F);
    }

    private static void animateTemperaturePress(StationView view, String control) {
        if (view.level().getBlockEntity(view.pos()) instanceof OvenBlockEntity oven) {
            if (pressedOven.get() != oven) {
                pressedOven = new WeakReference<>(oven);
                increasePressedAt = 0;
                decreasePressedAt = 0;
            }
            if (OvenStation.INCREASE.equals(control)) {
                increasePressedAt = Util.getMillis();
            } else {
                decreasePressedAt = Util.getMillis();
            }
        }
    }

    private static void updatePowerState(StationView view) {
        boolean powered = view.state().getControl(OvenStation.POWERED) > 0;
        for (int slot = 0; slot < OvenStation.SLOT_COUNT; slot++) {
            view.node("rack_" + slot).ifPresent(node -> node.setEnabled(!powered));
        }
        view.node(OvenStation.POWER).ifPresent(node -> {
            node.setTint(powered ? 0xFF67C99D : 0xFFBF664F);
            node.setTooltip(Component.translatable(powered ? "immersive_bakery.oven.power_off" : "immersive_bakery.oven.power_on"));
        });
    }

    private void updateTimer(StationView view) {
        float remaining = view.state().getControl(OvenStation.BAKING_REMAINING);
        timer.update(view, remaining > 0, remaining);
    }

    private static void setTemperatureTooltip(StationView view, String id) {
        view.node(id).ifPresent(node -> node.setTooltip(
                Component.translatable("immersive_bakery.oven." + id, OvenStation.TEMPERATURE_STEP)));
    }
}
