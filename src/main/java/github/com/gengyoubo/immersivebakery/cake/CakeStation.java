package github.com.gengyoubo.immersivebakery.cake;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import com.renyigesai.bakeries.init.BakeriesItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.level.block.AbstractCandleBlock;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.resources.ResourceLocation;

public final class CakeStation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, "custom_cake");
    public static final String ADD = "add";
    private CakeStation() { }
    public static void register() {
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
