package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.renyigesai.bakeries.block.toaster.ToasterBlock;
import com.renyigesai.bakeries.block.toaster.ToasterBlock.State;
import com.renyigesai.bakeries.block.toaster.ToasterBlockEntity;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.toaster.ToasterStation;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ToasterClient implements StationBehavior {
    private static final String LANG = "immersive_bakery.toaster.";
    private final BakingTimer timer = new BakingTimer();

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(ToasterStation.ID, view -> new ToasterClient()));
    }

    @Override
    public void onOpen(StationView view) {
        view.setInventoryFilter(stack -> view.level().getBlockEntity(view.pos()) instanceof ToasterBlockEntity toaster
                && ToasterStation.findRecipe(toaster, stack).isPresent());
    }

    @Override
    public void onSceneReady(StationView view) {
        timer.setup(view);
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        return click(view, node, button, true);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        var player = Minecraft.getInstance().player;
        if (event instanceof SceneEvent.Click click && player != null && player.inventoryMenu.getCarried().isEmpty()) {
            click(view, click.node(), click.button(), false);
        }
    }

    @Override
    public void tick(StationView view) {
        if (!view.isFocused() || !(view.level().getBlockEntity(view.pos()) instanceof ToasterBlockEntity toaster)) {
            return;
        }

        State state = toaster.getBlockState().getValue(ToasterBlock.STATE);
        int remaining = view.state().data().getInt(ToasterStation.REMAINING_TICKS);
        int total = view.state().data().getInt(ToasterStation.TOTAL_TICKS);
        float remainingFraction = switch (state) {
            case IDLE -> 1;
            case LIT -> total > 0 ? (float) remaining / total : 1;
            case FINISH -> 0;
        };
        timer.update(view, !toaster.isEmpty(), remainingFraction);
        String leverTooltip = switch (state) {
            case LIT -> null;
            case FINISH -> "finished";
            case IDLE -> toaster.isEmpty() ? "empty" : "press";
        };
        setTooltip(view, ToasterStation.LEVER, leverTooltip);
        slotTooltip(view, ToasterStation.SLOT_0, state, toaster.getItems().getStackInSlot(0));
        slotTooltip(view, ToasterStation.SLOT_1, state, toaster.getItems().getStackInSlot(1));
    }

    private static void slotTooltip(StationView view, String node, State state, ItemStack stack) {
        String key = switch (state) {
            case LIT -> null;
            case FINISH -> stack.isEmpty() ? "finished" : "take";
            case IDLE -> stack.isEmpty() ? "insert" : "take";
        };
        setTooltip(view, node, key);
    }

    private static void setTooltip(StationView view, String node, String key) {
        view.node(node).ifPresent(part -> part.setTooltip(key == null ? null : Component.translatable(LANG + key)));
    }

    private static boolean click(StationView view, String node, int button, boolean fromCursor) {
        if ((button != 0 && button != 1) || (!ToasterStation.SLOT_0.equals(node)
                && !ToasterStation.SLOT_1.equals(node) && !ToasterStation.LEVER.equals(node))) {
            return false;
        }
        CompoundTag data = new CompoundTag();
        data.putBoolean("cursor", fromCursor);
        view.sendAction(node, data);
        return true;
    }
}
