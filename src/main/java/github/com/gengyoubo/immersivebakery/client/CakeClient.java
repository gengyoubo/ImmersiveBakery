package github.com.gengyoubo.immersivebakery.client;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.renyigesai.bakeries.util.measurer.CakePartMeasurer;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.cake.CakeStation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import java.util.Objects;

@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CakeClient implements StationBehavior {
    private String next;
    private boolean initialized;
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(CakeStation.ID, view -> new CakeClient()));
    }
    @Override public void onOpen(StationView view) {
        initialized = false;
        refresh(view);
    }
    @Override public void frame(StationView view, float deltaSeconds) {
        refresh(view);
    }
    private void refresh(StationView view) {
        String wanted = CakeStation.nextPart(view.level().getBlockEntity(view.pos()));
        if (initialized && Objects.equals(next, wanted)) return;
        initialized = true;
        next = wanted;
        view.setInventoryFilter(stack -> ingredient(stack, wanted));
        String hint = wanted == null ? "complete" : switch (wanted) {
            case CakePartMeasurer.CAKE_BASE -> "base";
            case CakePartMeasurer.CAKE_FILLING -> "filling";
            case CakePartMeasurer.CAKE_CREAM -> "cream";
            default -> "topping";
        };
        view.showCaption(Component.translatable("immersive_bakery.custom_cake." + hint), 5);
    }
    private static boolean ingredient(ItemStack stack, String wanted) {
        return wanted != null && wanted.equals(CakePartMeasurer.getClientPartsType().get(BuiltInRegistries.ITEM.getKey(stack.getItem())));
    }
    @Override public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (!"cake".equals(node) || !ingredient(stack, CakeStation.nextPart(view.level().getBlockEntity(view.pos())))) return false;
        view.sendAction(CakeStation.ADD, new CompoundTag());
        return true;
    }
}
