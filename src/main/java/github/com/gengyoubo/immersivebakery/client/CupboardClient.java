package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.storage.CupboardStation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** A cupboard is nothing but its drawers. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CupboardClient implements StationBehavior {
    private final DrawerView drawers = new DrawerView("pull");

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(CupboardStation.ID, view -> new CupboardClient()));
        DrawerView.listen();
    }

    @Override
    public void onSceneReady(StationView view) {
        drawers.sceneReady(view);
    }

    @Override
    public void onOpen(StationView view) {
        drawers.open(view, true);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        drawers.clicked(view, event);
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        return drawers.pull(view, node);
    }

    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        drawers.store(view, slot, stack);
        return true;
    }

    @Override
    public boolean holdsBlock(StationView view) {
        return drawers.holdsBlock(view);
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        drawers.frame(view, deltaSeconds);
    }
}
