package github.com.gengyoubo.immersivebakery.oven;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType.Orientation;
import com.renyigesai.bakeries.block.oven.OvenBlockEntity;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import com.renyigesai.bakeries.recipe.oven.OvenRecipe;
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
public final class OvenStation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, "oven");
    public static final String INCREASE = "increase_temperature";
    public static final String DECREASE = "decrease_temperature";
    public static final String POWER = "power";
    public static final String POWERED = "powered";
    public static final String TEMPERATURE = "temperature";
    public static final String BAKING_REMAINING = "baking_remaining";
    public static final int MAX_TEMPERATURE = 500;
    public static final int TEMPERATURE_STEP = 5;
    static final String RACK = "rack";
    public static final int SLOT_COUNT = 6;

    private OvenStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(BakeriesBlocks.OVEN)
                .orientation(Orientation.BLOCK_OPPOSITE)
                .openWhen((player, pos, state) -> !player.isShiftKeyDown())
                .exclusive()
                .schema(StationSchema.builder()
                        .slots(RACK, SLOT_COUNT, SlotRules.DEFAULT.maxStack(1).kept())
                        .control(INCREASE, ControlSpec.button())
                        .control(DECREASE, ControlSpec.button())
                        .control(POWER, ControlSpec.button())
                        .control(POWERED, ControlSpec.toggle(false).locked())
                        .control(TEMPERATURE, ControlSpec.range(0, MAX_TEMPERATURE, 0).locked())
                        .control(BAKING_REMAINING, ControlSpec.range(0, 1, 0).locked())
                        .build())
                .logic(OvenLogic::new)
                .register();
    }

    public static boolean isTemperatureButton(String id) {
        return INCREASE.equals(id) || DECREASE.equals(id);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void restrictNativeMenu(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof OvenBlockEntity oven && OvenPower.isPowered(oven)) {
            event.setUseBlock(Event.Result.DENY);
        }
    }

    public static boolean canBake(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var recipes = level.getRecipeManager();
        if (level.isClientSide()) {
            // Bakeries' OvenRecipe.matches only runs on the server, but its ingredients are synced.
            return recipes.getAllRecipesFor(OvenRecipe.Type.INSTANCE).stream()
                    .anyMatch(recipe -> recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(stack)));
        }
        return recipes.getRecipeFor(OvenRecipe.Type.INSTANCE, new SimpleContainer(stack), level).isPresent();
    }
}
