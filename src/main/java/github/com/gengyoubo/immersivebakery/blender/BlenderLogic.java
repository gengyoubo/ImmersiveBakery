package github.com.gengyoubo.immersivebakery.blender;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.renyigesai.bakeries.block.blender.BlenderBlock;
import com.renyigesai.bakeries.block.blender.BlenderBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Also runs on clients near a blender nobody works at, where it only reads what the block entity holds. */
final class BlenderLogic implements StationLogic {
    /** The most sets one pick in the book puts in. */
    private static final int FILL_MOST = 16;
    @Nullable
    private final BlenderBlockEntity blender;
    private boolean headUp;
    private boolean unsorted;

    BlenderLogic(StationContext station) {
        blender = station.blockEntity() instanceof BlenderBlockEntity blockEntity ? blockEntity : null;
        if (blender != null) {
            station.state().bindSlots(BlenderStation.INGREDIENTS, new Slots(blender, 0, BlenderStation.INGREDIENT_SLOTS));
            station.state().bindSlots(BlenderStation.CONTAINER, new Slots(blender, BlenderStation.CONTAINER_SLOT, 1));
            station.state().bindSlots(BlenderStation.OUTPUT, new Slots(blender, BlenderStation.OUTPUT_SLOT, 1));
        }
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        if (isPresent(station)) {
            BlenderPower.useManualPower(blender);
            setHeadUp(station, !BlenderPower.isPowered(blender));
            unsorted = true;
            refresh(station);
        }
    }

    @Override
    public void onClose(StationContext station, Player player) {
        if (isPresent(station)) {
            setHeadUp(station, false);
        }
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (!isPresent(station) || BlenderPower.isPowered(blender)) {
            return false;
        }
        return switch (slot.group()) {
            case BlenderStation.INGREDIENTS -> !BlenderPower.isMixed(blender) && BlenderRecipes.isIngredient(station.level(), stack)
                    && BlenderLiquids.hasRoom(blender, stack);
            case BlenderStation.CONTAINER -> BlenderRecipes.isContainer(station.level(), stack);
            default -> false;
        };
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return isPresent(station) && !BlenderPower.isPowered(blender)
                && !(slot.group().equals(BlenderStation.INGREDIENTS) && BlenderPower.isMixed(blender))
                && !BlenderLiquids.isStored(station.state().getItem(slot));
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        unsorted |= slot.group().equals(BlenderStation.INGREDIENTS);
    }

    @Override
    public void tick(StationContext station) {
        if (!isPresent(station)) {
            return;
        }
        if (!station.isClientSide()) {
            boolean powered = BlenderPower.isPowered(blender);
            setHeadUp(station, !station.users().isEmpty() && !powered);
            if (unsorted && !powered && !BlenderPower.isMixed(blender) && !station.users().isEmpty()) {
                unsorted = false;
                Player user = station.users().iterator().next();
                storeLiquids(station, user);
                List<ItemStack> rest = BlenderRecipes.shareOut(blender);
                rest.forEach(stack -> give(user, stack));
                BlenderPower.useManualPower(blender);
                BlenderPower.sync(blender);
            }
        }
        refresh(station);
    }

    @Override
    public void onButton(StationContext station, Player player, String control) {
        if (isPresent(station) && blender.stillValid(player) && BlenderStation.POWER.equals(control)) {
            BlenderPower.toggle(blender);
            setHeadUp(station, !BlenderPower.isPowered(blender));
            refresh(station);
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!isPresent(station) || !blender.stillValid(player)) {
            return;
        }
        switch (action) {
            case BlenderStation.ACTION_SCOOP -> scoop(station, player);
            case BlenderStation.ACTION_SCOOP_FROM_INVENTORY -> scoopFromInventory(station, player);
            case BlenderStation.ACTION_DRAW -> draw(station, player);
            case BlenderStation.ACTION_DRAW_FROM_INVENTORY -> drawFromInventory(station, player);
            case BlenderStation.ACTION_FILL -> fill(station, player, data);
            default -> { return; }
        }
        refresh(station);
    }

    private void scoop(StationContext station, Player player) {
        fillCarried(station, player, vessel -> BlenderPower.scoop(blender, vessel));
    }

    private void scoopFromInventory(StationContext station, Player player) {
        fillFromInventory(station, player, vessel -> BlenderPower.scoop(blender, vessel));
    }

    private void draw(StationContext station, Player player) {
        if (mayDraw()) {
            fillCarried(station, player, vessel -> BlenderLiquids.draw(blender, vessel));
        }
    }

    private void drawFromInventory(StationContext station, Player player) {
        if (mayDraw()) {
            fillFromInventory(station, player, vessel -> BlenderLiquids.draw(blender, vessel));
        }
    }

    private boolean mayDraw() {
        return !BlenderPower.isPowered(blender) && !BlenderPower.isMixed(blender);
    }

    /** One of the empty vessels on the cursor is filled. */
    private void fillCarried(StationContext station, Player player, UnaryOperator<ItemStack> filling) {
        ItemStack carried = player.inventoryMenu.getCarried();
        ItemStack filled = carried.isEmpty() ? ItemStack.EMPTY : filling.apply(carried);
        if (!filled.isEmpty()) {
            ItemStack rest = carried.copyWithCount(carried.getCount() - 1);
            player.inventoryMenu.setCarried(rest.isEmpty() ? filled : rest);
            if (!rest.isEmpty()) {
                give(player, filled);
            }
            drawn(station);
        }
    }

    /** With an empty hand, the first fitting vessel of the inventory is filled. */
    private void fillFromInventory(StationContext station, Player player, UnaryOperator<ItemStack> filling) {
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            return;
        }
        // A bottle takes just a bottle of it, so it comes before a bucket.
        List<ItemStack> vessels = player.getInventory().items.stream()
                .sorted(Comparator.comparing(stack -> !stack.is(Items.GLASS_BOTTLE))).toList();
        for (ItemStack stack : vessels) {
            ItemStack filled = stack.isEmpty() ? ItemStack.EMPTY : filling.apply(stack);
            if (!filled.isEmpty()) {
                stack.shrink(1);
                player.getInventory().setChanged();
                give(player, filled);
                drawn(station);
                return;
            }
        }
    }

    private void drawn(StationContext station) {
        unsorted = true;
        BlenderPower.sync(blender);
        station.effect(BlenderStation.EFFECT_SCOOPED, "batter");
    }

    /** What was put in with its bottle or bucket is emptied into the bowl, and the vessel goes back to the player. */
    private void storeLiquids(StationContext station, Player player) {
        var inventory = blender.getInventory();
        for (int slot = 0; slot < BlenderStation.INGREDIENT_SLOTS; slot++) {
            ItemStack full = inventory.getStackInSlot(slot);
            if (!full.isEmpty() && BlenderLiquids.isLiquid(full) && !BlenderLiquids.isStored(full)) {
                inventory.setStackInSlot(slot, BlenderLiquids.store(full));
                poured(station, player, full);
            }
        }
    }

    private static void poured(StationContext station, Player player, ItemStack full) {
        give(player, BlenderLiquids.emptied(full));
        CompoundTag data = new CompoundTag();
        data.put(BlenderStation.POURED, full.save(new CompoundTag()));
        data.putInt(BlenderStation.BOTTLES, BlenderLiquids.store(full).getCount());
        station.effect(BlenderStation.EFFECT_POURED, "batter", data);
    }

    private void fill(StationContext station, Player player, CompoundTag data) {
        if (BlenderPower.isPowered(blender) || BlenderPower.isMixed(blender)) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(data.getString(BlenderStation.RECIPE));
        int sets = data.getBoolean(BlenderStation.ALL) ? FILL_MOST : 1;
        BlenderRecipes.all(player.level()).stream().filter(recipe -> recipe.getId().equals(id)).findFirst().ifPresent(recipe -> {
            List<ItemStack> poured = new ArrayList<>();
            if (BlenderRecipes.fill(blender, player.getInventory(), recipe, sets, poured)) {
                // One pour for all bottles of a liquid.
                List<ItemStack> pours = new ArrayList<>();
                for (ItemStack full : poured) {
                    pours.stream().filter(other -> ItemStack.isSameItemSameTags(other, full)).findFirst()
                            .ifPresentOrElse(other -> other.grow(1), () -> pours.add(full));
                }
                pours.forEach(full -> poured(station, player, full));
                player.getInventory().setChanged();
                BlenderPower.useManualPower(blender);
                BlenderPower.sync(blender);
            }
        });
    }

    private void refresh(StationContext station) {
        var state = station.state();
        boolean powered = station.isClientSide() ? station.blockState().getValue(BlenderBlock.POWERED) : BlenderPower.isPowered(blender);
        state.setControl(BlenderStation.POWERED, powered ? 1 : 0);
        state.setControl(BlenderStation.PROGRESS, Mth.clamp((float) blender.cookingTotalTime / BlenderStation.MIX_TICKS, 0, 1));
        state.setControl(BlenderStation.MIXED, !powered && BlenderPower.isMixed(blender) ? 1 : 0);
    }

    private boolean isPresent(StationContext station) {
        return blender != null && !blender.isRemoved() && station.blockEntity() == blender;
    }

    private void setHeadUp(StationContext station, boolean up) {
        if (headUp != up) {
            headUp = up;
            // Bakeries uses block event 0 to raise and lower the head with its menu.
            blender.sendBlockEvent(0, up ? 0 : 1);
            station.level().playSound(null, station.pos(), up ? blender.getOpenSound() : blender.getCloseSound(),
                    SoundSource.BLOCKS, 0.6F, 1);
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private record Slots(BlenderBlockEntity blender, int start, int size) implements SlotStorage {
        @Override
        public ItemStack get(int slot) {
            return blender.getInventory().getStackInSlot(start + slot);
        }

        @Override
        public void set(int slot, ItemStack stack) {
            if (!ItemStack.matches(get(slot), stack)) {
                // Bakeries' own setItem only accepts what its filter names.
                blender.getInventory().setStackInSlot(start + slot, stack.copy());
                BlenderPower.sync(blender);
            }
        }
    }
}
