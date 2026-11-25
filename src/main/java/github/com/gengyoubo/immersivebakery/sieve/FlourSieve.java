package github.com.gengyoubo.immersivebakery.sieve;

import com.renyigesai.bakeries.item.FlourSieveItem;
import com.renyigesai.bakeries.recipe.flour_sieve.FlourSieveRecipe;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.api.player.PlayerImmersion;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A sieve in the hand: what is put on it is shaken through, one at a time. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID)
public final class FlourSieve {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ImmersiveBakery.MOD_ID, "flour_sieve");
    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    /** What is on the sieve goes back into the inventory. */
    public static final String TAKE = "take";
    /** Followed by a slot of the inventory: all of its stack goes onto the sieve, or one of it. */
    public static final String PUT = "put/";
    public static final String PUT_ONE = "put_one/";
    public static final int STROKES = 6;
    private static final int TICKS_PER_STROKE = 3;

    private FlourSieve() {
    }

    public static void register() {
        PlayerImmersion.register(ID, player -> {
            InteractionHand hand = sieveHand(player);
            return hand == null ? null : new Sifting(hand, player.getItemInHand(hand));
        });
    }

    private static InteractionHand sieveHand(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof FlourSieveItem) {
                return hand;
            }
        }
        return null;
    }

    /** Sneaking keeps Bakeries' own way: held, for what is in the other hand. */
    @SubscribeEvent
    public static void use(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!(event.getItemStack().getItem() instanceof FlourSieveItem) || player.isShiftKeyDown() || player.isSpectator()) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer && !PlayerImmersion.open(serverPlayer, ID)) {
            return;
        }
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    public static Optional<FlourSieveRecipe> recipe(ItemStack ingredient, Level level) {
        // Bakeries' matches() is for the server only.
        return ingredient.isEmpty() ? Optional.empty() : level.getRecipeManager().getAllRecipesFor(FlourSieveRecipe.Type.INSTANCE).stream()
                .filter(recipe -> recipe.getIngredients().get(0).test(ingredient)).findFirst();
    }

    public static ItemStack result(ItemStack ingredient, Level level) {
        return recipe(ingredient, level).map(recipe -> recipe.getResultItem(level.registryAccess()).copy()).orElse(ItemStack.EMPTY);
    }

    static final class Sifting implements PlayerImmersion.Session {
        private final InteractionHand hand;
        private final ItemStack tool;
        private ItemStack input = ItemStack.EMPTY;
        private ItemStack made = ItemStack.EMPTY;
        private int batches;
        private int progress;
        private String lastStroke = "";
        private long nextStroke;

        Sifting(InteractionHand hand, ItemStack tool) {
            this.hand = hand;
            this.tool = tool;
        }

        @Override
        public boolean isValid(ServerPlayer player) {
            return !tool.isEmpty() && player.getItemInHand(hand) == tool;
        }

        @Override
        public CompoundTag state() {
            CompoundTag state = new CompoundTag();
            state.put("tool", tool.copy().save(new CompoundTag()));
            state.put("input", input.save(new CompoundTag()));
            state.put("made", made.save(new CompoundTag()));
            state.putInt("batches", batches);
            state.putInt("progress", progress);
            return state;
        }

        @Override
        public float pose() {
            return RIGHT.equals(lastStroke) ? 1 : LEFT.equals(lastStroke) ? -1 : 0;
        }

        @Override
        public boolean onAction(ServerPlayer player, String action) {
            if (action.equals(LEFT) || action.equals(RIGHT)) {
                return stroke(player, action);
            }
            if (action.equals(TAKE)) {
                return giveBack(player);
            }
            boolean one = action.startsWith(PUT_ONE);
            if (!one && !action.startsWith(PUT)) {
                return false;
            }
            try {
                return put(player, Integer.parseInt(action.substring((one ? PUT_ONE : PUT).length())), one);
            } catch (NumberFormatException noSlot) {
                return false;
            }
        }

        private boolean put(ServerPlayer player, int slot, boolean one) {
            Inventory inventory = player.getInventory();
            if (slot < 0 || slot >= inventory.items.size() && slot != Inventory.SLOT_OFFHAND) {
                return false;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack == tool || result(stack, player.level()).isEmpty()) {
                return false;
            }
            if (!input.isEmpty() && !ItemStack.isSameItemSameTags(input, stack)) {
                // Out of the slot first, so that what comes off the sieve may go there.
                ItemStack put = one ? stack.split(1) : inventory.removeItemNoUpdate(slot);
                giveBack(player);
                input = put;
            } else {
                int moved = Math.min(one ? 1 : stack.getCount(), stack.getMaxStackSize() - input.getCount());
                if (moved <= 0) {
                    return false;
                }
                ItemStack put = stack.split(moved);
                if (input.isEmpty()) {
                    input = put;
                } else {
                    input.grow(moved);
                }
            }
            player.inventoryMenu.broadcastChanges();
            return true;
        }

        private boolean giveBack(ServerPlayer player) {
            if (input.isEmpty()) {
                return false;
            }
            player.getInventory().placeItemBackInInventory(input);
            player.inventoryMenu.broadcastChanges();
            input = ItemStack.EMPTY;
            progress = 0;
            return true;
        }

        private boolean stroke(ServerPlayer player, String side) {
            long now = player.level().getGameTime();
            if (now < nextStroke || side.equals(lastStroke)) {
                return false;
            }
            lastStroke = side;
            nextStroke = now + TICKS_PER_STROKE;
            ItemStack result = result(input, player.level());
            if (result.isEmpty()) {
                // The arms are seen to move all the same.
                return true;
            }
            if (++progress == STROKES) {
                progress = 0;
                batches++;
                made = result.copy();
                input.shrink(1);
                // As Bakeries does.
                tool.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(hand));
                player.getInventory().placeItemBackInInventory(result);
                player.inventoryMenu.broadcastChanges();
            }
            return true;
        }

        @Override
        public void onClose(ServerPlayer player) {
            giveBack(player);
        }
    }
}
