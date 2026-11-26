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

@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CakeClient implements StationBehavior {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(CakeStation.ID, view -> new CakeClient()));
    }
    @Override public void onOpen(StationView view) {
        view.setInventoryFilter(CakeClient::ingredient);
        view.showCaption(Component.translatable("immersive_bakery.custom_cake.hint"), 5);
    }
    private static boolean ingredient(ItemStack stack) {
        return CakePartMeasurer.getClientPartsType().containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
    @Override public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (!"cake".equals(node) || !ingredient(stack)) return false;
        view.sendAction(CakeStation.ADD, new CompoundTag());
        return true;
    }
}
