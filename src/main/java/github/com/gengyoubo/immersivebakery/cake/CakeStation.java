package github.com.gengyoubo.immersivebakery.cake;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import com.renyigesai.bakeries.init.BakeriesItems;
import com.renyigesai.bakeries.block.custom_cake.CustomCakeBlockEntity;
import com.renyigesai.bakeries.util.measurer.CakePartMeasurer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.level.block.AbstractCandleBlock;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public final class CakeStation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, "custom_cake");
    public static final String ADD = "add";
    private CakeStation() { }
    /** Returns the next layer that may be added, or null once all layers are present. */
    public static String nextPart(BlockEntity entity) {
        if (!(entity instanceof CustomCakeBlockEntity cake)) return CakePartMeasurer.CAKE_BASE;
        byte[] used = cake.getPartUse();
        String[] types = {CakePartMeasurer.CAKE_BASE, CakePartMeasurer.CAKE_FILLING,
                CakePartMeasurer.CAKE_CREAM, CakePartMeasurer.CAKE_TOPPING};
        for (int i = 0; i < types.length; i++) {
            if (i >= used.length || used[i] == 0) return types[i];
        }
        return null;
    }

    /** Keep ordinary right-clicks subject to the same order as the station. */
    private static void onUseCake(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getLevel().getBlockEntity(event.getPos()) instanceof CustomCakeBlockEntity cake)) return;
        var part = CakePartMeasurer.isIngredient(event.getItemStack()).orElse(null);
        if (part != null && !part.getType().equals(nextPart(cake))) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(CakeStation::onUseCake);
        Diegesis.station(ID).block(BakeriesBlocks.SILICONE_PAPER).block(BakeriesBlocks.CUSTOM_CAKE)
                .orientation(Orientation.BLOCK_OPPOSITE)
                .openWhen((player, pos, state) -> {
                    var held = player.getMainHandItem();
                    return !player.isShiftKeyDown() && !held.is(BakeriesItems.CUT_CAKE_BASE.get())
                            && !(held.getItem() instanceof NameTagItem) && !(held.getItem() instanceof FlintAndSteelItem)
                            && !(held.getItem() instanceof BlockItem item && item.getBlock() instanceof AbstractCandleBlock);
                })
                .exclusive().schema(StationSchema.builder().build()).logic(CakeLogic::new).register();
    }
}
