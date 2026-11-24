package github.com.gengyoubo.immersivebakery.fermentation;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.block.fermentation_box.FermentationBoxBlockEntity;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import com.renyigesai.bakeries.init.BakeriesRecipeTypes;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID)
public final class FermentationStation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, "fermentation_box");
    public static final String POWER = "power";
    public static final String POWERED = "powered";
    public static final String DECREASE = "decrease_duration";
    public static final String INCREASE = "increase_duration";
    public static final String DURATION = "duration";
    public static final String TEMPERATURE = "temperature";
    public static final String PERFECT_TIME = "perfect_time";
    public static final String REMAINING = "fermentation_remaining";
    public static final int SLOT_COUNT = 6;
    public static final int DURATION_STEP = 10;
    public static final int MIN_DURATION = 430;
    public static final int MAX_DURATION = 1200;
    static final String RACK = "rack";

    private FermentationStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.FERMENTATION_BOX)
                .orientation(Orientation.BLOCK_OPPOSITE)
                .openWhen((player, pos, state) -> !player.isShiftKeyDown())
                .exclusive()
                .schema(StationSchema.builder()
                        .slots(RACK, SLOT_COUNT, SlotRules.DEFAULT.maxStack(1).kept())
                        .control(POWER, ControlSpec.button())
                        .control(DECREASE, ControlSpec.button())
                        .control(INCREASE, ControlSpec.button())
                        .control(POWERED, ControlSpec.toggle(false).locked())
                        .control(DURATION, ControlSpec.range(MIN_DURATION, MAX_DURATION, MIN_DURATION).locked())
                        .control(TEMPERATURE, ControlSpec.range(-100, 100, 0).locked())
                        .control(PERFECT_TIME, ControlSpec.range(0, MAX_DURATION, 0).locked())
                        .control(REMAINING, ControlSpec.range(0, 1, 0).locked())
                        .build())
                .logic(FermentationLogic::new)
                .register();
    }

    public static boolean isDurationButton(String control) {
        return DECREASE.equals(control) || INCREASE.equals(control);
    }

    public static boolean canFerment(Level level, ItemStack stack) {
        return !stack.isEmpty() && level.getRecipeManager()
                .getRecipeFor(BakeriesRecipeTypes.FERMENTATION_BOX.get(), new SimpleContainer(stack), level).isPresent();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void restrictNativeMenu(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof FermentationBoxBlockEntity box
                && FermentationPower.isPowered(box)) {
            event.setUseBlock(Event.Result.DENY);
        }
    }
}
