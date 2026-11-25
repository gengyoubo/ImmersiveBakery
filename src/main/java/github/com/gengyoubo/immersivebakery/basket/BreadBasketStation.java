package github.com.gengyoubo.immersivebakery.basket;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.storage.PlacedItems;
import net.minecraft.resources.ResourceLocation;

/** A basket that things are put into by hand, wherever there is room, and lie there for everyone to see. */
public final class BreadBasketStation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, "bread_basket");
    public static final String BREADS = "breads";
    /** As many stacks as the basket keeps: room runs out first. */
    public static final int SLOT_COUNT = 64;
    /** As high as things are heaped by themselves, a little over the rim, pixels. */
    public static final float HEAPED = 12;
    /** As many of them as the menu of Bakeries shows. */
    static final int MENU_SLOTS = 5;
    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(BREADS, SLOT_COUNT, SlotRules.DEFAULT.placed().kept())
            .build();

    private BreadBasketStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.BREAD_BASKET)
                .orientation(Orientation.BLOCK_OPPOSITE)
                .openWhen((player, pos, state) -> !player.isShiftKeyDown())
                .idleLogic()
                .schema(SCHEMA)
                .logic(BreadBasketLogic::new)
                .register();
    }

    public static PlacedItems items(StationState state) {
        return new PlacedItems(state, BREADS, 0, SLOT_COUNT);
    }
}
