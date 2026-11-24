package github.com.gengyoubo.immersivebakery.kiln;

import com.renyigesai.bakeries.item.StoneKilnShovelItem;
import com.renyigesai.bakeries.recipe.StoneKilnRecipe;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ItemStackedOnOtherEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bakeries' oven peel carries one item in its data; here it is loaded and unloaded by hand, too. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID)
public final class Peel {
    private Peel() {
    }

    public static boolean isPeel(ItemStack stack) {
        return stack.getItem() instanceof StoneKilnShovelItem;
    }

    /** What a peel carries. Bakeries' own getters add an empty tag to every peel they look at. */
    public static ItemStack load(ItemStack peel) {
        return isPeel(peel) && peel.hasTag() ? ((StoneKilnShovelItem) peel.getItem()).getInventoryStack(peel) : ItemStack.EMPTY;
    }

    public static void setLoad(ItemStack peel, ItemStack load) {
        if (peel.getItem() instanceof StoneKilnShovelItem item) {
            // Bakeries only adds to an empty peel.
            item.removeItem(peel);
            if (!load.isEmpty()) {
                item.addItem(peel, load.copyWithCount(1));
            } else if (peel.hasTag()) {
                peel.removeTagKey("Inventory");
            }
        }
    }

    /** Whether the kiln bakes an item: dough and pizzas, and what a smoker cooks. */
    public static boolean isRaw(Level level, ItemStack stack) {
        if (stack.isEmpty() || isPeel(stack)) {
            return false;
        }
        var recipes = level.getRecipeManager();
        // Bakeries' StoneKilnRecipe.matches only runs on the server, but its ingredients are synced.
        return recipes.getAllRecipesFor(StoneKilnRecipe.Type.INSTANCE).stream().anyMatch(recipe -> recipe.getIngredients().get(0).test(stack))
                || recipes.getAllRecipesFor(RecipeType.SMOKING).stream().anyMatch(recipe -> recipe.getIngredients().get(0).test(stack));
    }

    /** With the peel in one hand and dough in the other, a use loads the peel. */
    @SubscribeEvent
    public static void loadFromOtherHand(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack peel = event.getItemStack();
        ItemStack raw = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (player.isShiftKeyDown() || !isPeel(peel) || !load(peel).isEmpty() || !isRaw(event.getLevel(), raw)) {
            return;
        }
        if (!event.getLevel().isClientSide()) {
            setLoad(peel, raw);
            if (!player.getAbilities().instabuild) {
                raw.shrink(1);
            }
            event.getLevel().playSound(null, player.blockPosition(), SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.8F, 0.8F);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
    }

    /** In an inventory the peel is handled like a bundle: right clicks put one item on it or take it off. */
    @SubscribeEvent
    public static void handleInInventory(ItemStackedOnOtherEvent event) {
        if (event.getClickAction() != ClickAction.SECONDARY || !event.getSlot().allowModification(event.getPlayer())) {
            return;
        }
        ItemStack inSlot = event.getSlot().getItem();
        ItemStack carried = event.getCarriedSlotAccess().get();
        boolean handled = isPeel(carried) ? peelOnSlot(event, carried, inSlot) : isPeel(inSlot) && cursorOnPeel(event, carried, inSlot);
        if (handled) {
            event.getPlayer().playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 1);
            event.setCanceled(true);
        }
    }

    private static boolean peelOnSlot(ItemStackedOnOtherEvent event, ItemStack peel, ItemStack inSlot) {
        ItemStack load = load(peel);
        if (load.isEmpty()) {
            if (!isRaw(event.getPlayer().level(), inSlot)) {
                return false;
            }
            ItemStack taken = event.getSlot().safeTake(1, 1, event.getPlayer());
            setLoad(peel, taken);
            return !taken.isEmpty();
        }
        if (!inSlot.isEmpty() && !ItemStack.isSameItemSameTags(inSlot, load) || !event.getSlot().safeInsert(load.copy()).isEmpty()) {
            return false;
        }
        setLoad(peel, ItemStack.EMPTY);
        return true;
    }

    private static boolean cursorOnPeel(ItemStackedOnOtherEvent event, ItemStack carried, ItemStack peel) {
        ItemStack load = load(peel);
        if (carried.isEmpty() && !load.isEmpty()) {
            event.getCarriedSlotAccess().set(load.copy());
            setLoad(peel, ItemStack.EMPTY);
        } else if (load.isEmpty() && isRaw(event.getPlayer().level(), carried)) {
            setLoad(peel, carried);
            carried.shrink(1);
        } else {
            return false;
        }
        event.getSlot().setChanged();
        return true;
    }
}
