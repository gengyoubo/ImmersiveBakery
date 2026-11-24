package github.com.gengyoubo.immersivebakery.cup;

import com.pockyl.diegesis.api.Diegesis;
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

    private CupStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.DRINK_CUP)
                .orientation(Orientation.PLAYER)
                .openWhen((player, pos, state) -> !usesItem(player))
                .exclusive()
                .hidesBlock()
                .logic(CupLogic::new)
                .register();
    }

    /** Sneaking with something in the hand never reaches the cup. With an empty hand Bakeries would hand out a drink nobody shook. */
    private static boolean usesItem(Player player) {
        return player.isShiftKeyDown() && !(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, path);
    }
}
