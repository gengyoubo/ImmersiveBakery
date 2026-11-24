package github.com.gengyoubo.immersivebakery.rack;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.resources.ResourceLocation;

public final class BreadRackStation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, "bread_rack");
    static final String SHELVES = "shelves";
    static final int SLOT_COUNT = 4;

    private BreadRackStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.BREAD_RACK)
                .block(BakeriesBlocks.GLASS_BREAD_RACK)
                .orientation(Orientation.BLOCK_OPPOSITE)
                .openWhen((player, pos, state) -> !player.isShiftKeyDown())
                .schema(StationSchema.builder()
                        .slots(SHELVES, SLOT_COUNT, SlotRules.DEFAULT.maxStack(1).kept())
                        .build())
                .logic(BreadRackLogic::new)
                .register();
    }
}
