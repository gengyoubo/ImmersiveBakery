package github.com.gengyoubo.immersivebakery.cup;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class CupStation {
    public static final ResourceLocation ID = id("drink_cup");
    /** Something went in; data: {@link #ITEM}. */
    public static final ResourceLocation EFFECT_ADDED = id("cup_added");
    public static final String ITEM = "item";
    /** One of what is on the cursor goes in. */
    public static final String ACTION_ADD = "add";
    /** What went in last comes out again. */
    public static final String ACTION_TAKE = "take";
    /** A recipe picked in the book; data: {@link #RECIPE}, its id. */
    public static final String ACTION_FILL = "fill";
    public static final String RECIPE = "recipe";
    /** The cup was shaken until all in it was mixed. */
    public static final String ACTION_SHAKEN = "shaken";

    /** The finished drink's own cup is full. */
    public static final String ACTION_POURED = "poured";
    /** What belongs on the drink fell into it, the straw last. */
    public static final String ACTION_DONE = "done";
    /** How far the drink is. */
    public static final String STAGE = "stage";
    public static final int MIXING = 0;
    public static final int SHAKEN = 1;
    public static final int POURED = 2;
    public static final int DONE = 3;

    private CupStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.DRINK_CUP)
                .orientation(Orientation.PLAYER)
                .openWhen((player, pos, state) -> !usesItem(player))
                .exclusive()
                .hidesBlock()
                .schema(StationSchema.builder().control(STAGE, ControlSpec.stepped(MIXING, DONE, DONE, MIXING).locked()).build())
                .logic(CupLogic::new)
                .register();
    }

    /** Sneaking with something in the hand never reaches the cup. With an empty hand Bakeries would hand out a drink nobody shook. */
    private static boolean usesItem(Player player) {
        return player.isShiftKeyDown() && !(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty());
    }

    public static int stage(StationState state) {
        return Math.round(state.getControl(STAGE));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, path);
    }
}
