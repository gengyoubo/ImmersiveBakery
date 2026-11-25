package github.com.gengyoubo.immersivebakery.storage;

import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Pulls out and pushes in the drawers and keeps what lies in them in the block's own container. */
public class DrawerLogic implements StationLogic {
    @Nullable
    private final BlockEntity cupboard;
    /** Something was taken or came down: what was made smaller to fit may have room for its own size. */
    private boolean roomier;

    public DrawerLogic(StationContext station) {
        cupboard = station.blockEntity() instanceof Container ? station.blockEntity() : null;
        if (cupboard instanceof Container container) {
            station.state().bindSlots(Drawers.ITEMS, SlotStorage.of(container, 0, Drawers.SLOT_COUNT));
        }
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        Drawers.shut(station.state());
    }

    /** Nobody leaves them standing open. */
    @Override
    public void onClose(StationContext station, Player player) {
        if (station.users().stream().allMatch(user -> user == player) && Drawers.shut(station.state())) {
            sound(station, SoundEvents.BARREL_CLOSE);
        }
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return isPresent(station);
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return isPresent(station);
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!isPresent(station)) {
            return;
        }
        int drawer = data.getInt(Drawers.DRAWER);
        switch (action) {
            case Drawers.ACTION_SIDE -> Drawers.setSide(station.state(), data.getInt(Drawers.ON_SIDE));
            case Drawers.ACTION_DRAWER -> {
                if (drawer >= 0 && drawer < Drawers.COUNT) {
                    boolean open = !Drawers.isOpen(station.state(), drawer);
                    Drawers.setOpen(station.state(), drawer, open);
                    sound(station, open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE);
                }
            }
            default -> {
                if (drawer >= 0 && drawer < Drawers.COUNT) {
                    Drawers.items(station.state(), drawer).act(player.getInventory(), action, data);
                }
            }
        }
    }

    private static void sound(StationContext station, SoundEvent sound) {
        station.level().playSound(null, station.pos(), sound, SoundSource.BLOCKS, 0.4F, 1.3F);
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        roomier |= station.state().getItem(slot).isEmpty();
    }

    @Override
    public void onPoseChanged(StationContext station, Player player, SlotRef slot, ItemPose pose) {
        roomier = true;
    }

    @Override
    public void tick(StationContext station) {
        if (!isPresent(station)) {
            return;
        }
        Drawers.sort(station.state(), stack -> giveBack(station, stack));
        for (int drawer = 0; drawer < Drawers.COUNT; drawer++) {
            PlacedItems items = Drawers.items(station.state(), drawer);
            items.forgetTaken();
            if (roomier) {
                float ceiling = Drawers.ceiling(drawer);
                items.growBack(pose -> ceiling);
            }
        }
        roomier = false;
    }

    private static void giveBack(StationContext station, ItemStack stack) {
        Player player = station.users().stream().findFirst().orElse(null);
        if (player == null) {
            Block.popResource(station.level(), station.pos(), stack);
        } else if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    protected final boolean isPresent(StationContext station) {
        return cupboard != null && !cupboard.isRemoved() && station.blockEntity() == cupboard;
    }
}
