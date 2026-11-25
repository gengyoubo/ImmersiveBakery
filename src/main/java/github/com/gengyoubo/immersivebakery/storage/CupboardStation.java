package github.com.gengyoubo.immersivebakery.storage;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.resources.ResourceLocation;

/** A cupboard with two drawers that things are put into by hand, wherever there is room. All four sides have them. */
public final class CupboardStation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, "cupboard");
    public static final StationSchema SCHEMA = Drawers.addTo(StationSchema.builder()).build();

    private CupboardStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.CUPBOARD)
                .orientation(Orientation.PLAYER)
                .openWhen((player, pos, state) -> !player.isShiftKeyDown())
                .exclusive()
                .hidesBlock()
                .schema(SCHEMA)
                .logic(DrawerLogic::new)
                .register();
    }
}
