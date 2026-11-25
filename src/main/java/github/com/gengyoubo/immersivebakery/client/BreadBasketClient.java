package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.basket.BreadBasketStation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** The bread basket: whole stacks of the inventory go into it as long as there is room. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BreadBasketClient implements StationBehavior {
    private static final float HINT_SECONDS = 5;
    private static final float CAPTION_SECONDS = 2.5F;
    /** As the scene has them. */
    private static final PlacedItemsView.Area INSIDE = new PlacedItemsView.Area(5, 5, 11, 11, 1, BreadBasketStation.HEAPED);
    private static final float ITEM_SIZE = 5;
    private static final float ASKEW = 0.12F;

    private final PlacedItemsView items = new PlacedItemsView(BreadBasketStation::items, ITEM_SIZE, ASKEW, INSIDE, new CompoundTag());

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(BreadBasketStation.ID, view -> new BreadBasketClient()));
    }

    @Override
    public void onOpen(StationView view) {
        view.showCaption(Component.translatable("immersive_bakery.placed_items.hint"), HINT_SECONDS);
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        items.arrange(view, deltaSeconds);
    }

    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!items.store(view, slot, stack)) {
            view.showCaption(Component.translatable("immersive_bakery.bread_basket.full"), CAPTION_SECONDS);
        }
        return true;
    }
}
