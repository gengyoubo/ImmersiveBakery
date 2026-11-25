package github.com.gengyoubo.immersivebakery.bread;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** Bread and cake to be cut: first out of the mould, then up. */
public final class BreadStation {
    public static final ResourceLocation ID = id("bread");
    public static final ResourceLocation EFFECT_LOOSENED = id("bread_loosened");
    public static final ResourceLocation EFFECT_CUT = id("bread_cut");
    /** The knife at work here; it goes back to the player. */
    public static final String KNIFE = "knife";
    /** The station's data: the lines that are cut and have not come to anything yet, a bit each. */
    public static final String CUT = "cut";
    /** The knife went all the way round in the mould. */
    public static final String ACTION_LOOSEN = "loosen";
    /** The knife went along a line; data: {@link #LINE}. */
    public static final String ACTION_CUT = "cut";
    public static final String LINE = "line";

    private BreadStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.MOULD_TOAST)
                .block(BakeriesBlocks.MOULD_CHEESE_COCOA_TOAST)
                .block(BakeriesBlocks.MOULD_POUND_CAKE)
                .block(BakeriesBlocks.TOAST)
                .block(BakeriesBlocks.CHEESE_COCOA_TOAST)
                .block(BakeriesBlocks.POUND_CAKE)
                .block(BakeriesBlocks.COUNTRY_BREAD)
                .block(BakeriesBlocks.MOULD_CAKE_BASE)
                .block(BakeriesBlocks.MOULD_CARROT_CAKE)
                .block(BakeriesBlocks.MOULD_BASQUE_CAKE)
                .block(BakeriesBlocks.MOULD_MATCHA_CAKE)
                .block(BakeriesBlocks.MOULD_RED_VELVET_CAKE)
                .block(BakeriesBlocks.CAKE_BASE)
                .block(BakeriesBlocks.MULTI_LAYER_CREAM_CAKE)
                .when(BreadStation::isBread)
                .orientation(Orientation.BLOCK_OPPOSITE)
                .openWhen((player, pos, state) -> opens(player, state))
                .exclusive()
                .schema(StationSchema.builder().slots(KNIFE, 1, SlotRules.DEFAULT.maxStack(1).accepts(BreadCutting::isKnife)).build())
                .logic(BreadLogic::new)
                .register();
    }

    private static boolean isBread(BlockState state) {
        return BreadCutting.isInMould(state) || BreadCutting.isCuttable(state);
    }

    /** With another of the same in the hand, that goes beside it as in Bakeries; cake is eaten unless there is a knife in the hand. */
    public static boolean opens(Player player, BlockState state) {
        return isBread(state) && !player.isShiftKeyDown() && !player.getMainHandItem().is(state.getBlock().asItem())
                && (!BreadCutting.isCake(state) || BreadCutting.isKnife(player.getMainHandItem()));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, path);
    }
}
