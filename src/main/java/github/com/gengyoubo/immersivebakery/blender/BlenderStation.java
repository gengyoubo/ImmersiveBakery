package github.com.gengyoubo.immersivebakery.blender;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.block.blender.BlenderBlockEntity;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID)
public final class BlenderStation {
    public static final ResourceLocation ID = id("blender");
    public static final ResourceLocation EFFECT_SCOOPED = id("scooped");
    /** A liquid was poured into the bowl; data: {@link #POURED}, the full vessel, and {@link #BOTTLES}, how much of it. */
    public static final ResourceLocation EFFECT_POURED = id("poured");
    public static final String POURED = "poured";
    public static final String BOTTLES = "bottles";
    public static final String INGREDIENTS = "ingredients";
    public static final String CONTAINER = "container";
    public static final String OUTPUT = "result";
    public static final String POWER = "power";
    public static final String POWERED = "powered";
    public static final String PROGRESS = "progress";
    /** The batter is ready and waits to be scooped with a container. */
    public static final String MIXED = "mixed";
    public static final String ACTION_SCOOP = "scoop";
    public static final String ACTION_SCOOP_FROM_INVENTORY = "scoop_from_inventory";
    /** An empty bottle or bucket, on the cursor or from the inventory, is filled with a liquid poured in before. */
    public static final String ACTION_DRAW = "draw";
    public static final String ACTION_DRAW_FROM_INVENTORY = "draw_from_inventory";
    /** A recipe picked in the book; data: {@link #RECIPE}, its id, and {@link #ALL}, as many sets as fit instead of one. */
    public static final String ACTION_FILL = "fill";
    public static final String RECIPE = "recipe";
    public static final String ALL = "all";
    /** Bakeries mixes every recipe for this long. */
    public static final int MIX_TICKS = 100;
    public static final int INGREDIENT_SLOTS = 9;
    static final int CONTAINER_SLOT = 9;
    static final int OUTPUT_SLOT = 10;

    private BlenderStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.BLENDER)
                .orientation(Orientation.BLOCK_OPPOSITE)
                .openWhen((player, pos, state) -> !player.isShiftKeyDown())
                .exclusive()
                .idleLogic()
                .schema(StationSchema.builder()
                        .slots(INGREDIENTS, INGREDIENT_SLOTS, SlotRules.DEFAULT.kept())
                        .slots(CONTAINER, 1, SlotRules.DEFAULT.kept())
                        .slots(OUTPUT, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
                        .control(POWER, ControlSpec.button())
                        .control(POWERED, ControlSpec.toggle(false).locked())
                        .control(PROGRESS, ControlSpec.stepped(0, 1, MIX_TICKS / 2, 0).locked())
                        .control(MIXED, ControlSpec.toggle(false).locked())
                        .build())
                .logic(BlenderLogic::new)
                .register();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void restrictNativeMenu(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof BlenderBlockEntity blender
                && !BlenderPower.allowsNativeMenu(blender)) {
            event.setUseBlock(Event.Result.DENY);
            if (BlenderLiquids.hasStored(blender)) {
                event.getEntity().displayClientMessage(Component.translatable("immersive_bakery.blender.holds_liquid"), true);
            }
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, path);
    }
}
