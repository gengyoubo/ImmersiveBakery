package github.com.gengyoubo.immersivebakery.basket;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;

/** The first of a basket's slots, the ones its own menu has. */
public record BasketMenuSlots(Container basket) implements Container {
    public static AbstractContainerMenu menu(int id, Inventory inventory, Container basket) {
        return new HopperMenu(id, inventory, new BasketMenuSlots(basket));
    }

    @Override
    public int getContainerSize() {
        return BreadBasketStation.MENU_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return basket.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return basket.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return basket.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        basket.setItem(slot, stack);
    }

    @Override
    public void setChanged() {
        basket.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return basket.stillValid(player);
    }

    @Override
    public void startOpen(Player player) {
        basket.startOpen(player);
    }

    @Override
    public void stopOpen(Player player) {
        basket.stopOpen(player);
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < getContainerSize(); i++) {
            setItem(i, ItemStack.EMPTY);
        }
    }
}
