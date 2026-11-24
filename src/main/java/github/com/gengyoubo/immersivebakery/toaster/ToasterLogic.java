package github.com.gengyoubo.immersivebakery.toaster;

import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.renyigesai.bakeries.block.toaster.ToasterBlock;
import com.renyigesai.bakeries.block.toaster.ToasterBlock.State;
import com.renyigesai.bakeries.block.toaster.ToasterBlockEntity;
import com.renyigesai.bakeries.config.BakeriesConfig;
import com.renyigesai.bakeries.init.BakeriesSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

final class ToasterLogic implements StationLogic {
    @Override
    public void onOpen(StationContext station, Player player) {
        // Bakeries' direct interaction can leave an empty toaster in the heating state.
        if (station.blockEntity() instanceof ToasterBlockEntity toaster) {
            if (toaster.isEmpty() && station.blockState().getValue(ToasterBlock.STATE) == State.LIT) {
                station.level().setBlockAndUpdate(station.pos(), station.blockState().setValue(ToasterBlock.STATE, State.IDLE));
            }
            syncTimer(station, toaster);
        }
    }

    @Override
    public void tick(StationContext station) {
        if (station.level().getGameTime() % 5 == 0
                && station.blockEntity() instanceof ToasterBlockEntity toaster) {
            syncTimer(station, toaster);
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(station.blockEntity() instanceof ToasterBlockEntity toaster)) {
            return;
        }

        State state = station.blockState().getValue(ToasterBlock.STATE);
        if (state == State.LIT) {
            return;
        }

        switch (action) {
            case ToasterStation.SLOT_0 -> useSlot(station, toaster, state, serverPlayer, 0, data.getBoolean("cursor"));
            case ToasterStation.SLOT_1 -> useSlot(station, toaster, state, serverPlayer, 1, data.getBoolean("cursor"));
            case ToasterStation.LEVER -> {
                if (state == State.IDLE && !toaster.isEmpty()) {
                    toaster.changeState(0);
                    station.level().setBlockAndUpdate(station.pos(), station.blockState().setValue(ToasterBlock.STATE, State.LIT));
                    syncTimer(station, toaster);
                    playSound(station, BakeriesSounds.TOASTER_IN.get());
                }
            }
        }
    }

    private static void useSlot(StationContext station, ToasterBlockEntity toaster, State state,
                                ServerPlayer player, int slot, boolean fromCursor) {
        ItemStack cursor = player.inventoryMenu.getCarried();
        if (fromCursor == cursor.isEmpty()) {
            return;
        }

        var items = toaster.getItems();
        ItemStack inSlot = items.getStackInSlot(slot);
        if (!inSlot.isEmpty()) {
            if (!fromCursor) {
                items.setStackInSlot(slot, ItemStack.EMPTY);
                toaster.getCookingTime()[slot] = 0;
                toaster.getCookingProgress()[slot] = 0;
                if (state == State.FINISH && toaster.isEmpty()) {
                    station.level().setBlockAndUpdate(station.pos(), station.blockState().setValue(ToasterBlock.STATE, State.IDLE));
                }
                toaster.updateBlock();
                ItemHandlerHelper.giveItemToPlayer(player, inSlot);
                playSound(station, SoundEvents.ITEM_FRAME_REMOVE_ITEM);
            }
            return;
        }

        if (state != State.IDLE) {
            return;
        }
        ItemStack input = fromCursor ? cursor : player.getMainHandItem();
        var recipe = ToasterStation.findRecipe(toaster, input).orElse(null);
        if (recipe == null) {
            return;
        }

        // Bakeries' addItem chooses the first empty slot. Initialise the selected slot with the same timer.
        toaster.getCookingTime()[slot] = (int) (recipe.getCookingTime() / BakeriesConfig.toasterDoubleSpeed);
        toaster.getCookingProgress()[slot] = 0;
        items.setStackInSlot(slot, input.copyWithCount(1));
        if (!player.isCreative()) {
            input.shrink(1);
            if (fromCursor && input.isEmpty()) {
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
            }
        }
        toaster.updateBlock();
        playSound(station, SoundEvents.ITEM_FRAME_ADD_ITEM);
    }

    private static void syncTimer(StationContext station, ToasterBlockEntity toaster) {
        int total = 0;
        int remaining = 0;
        if (toaster.getBlockState().getValue(ToasterBlock.STATE) == State.LIT) {
            for (int slot = 0; slot < toaster.getItems().getSlots(); slot++) {
                if (!toaster.getItems().getStackInSlot(slot).isEmpty()) {
                    int duration = toaster.getCookingTime()[slot];
                    total = Math.max(total, duration);
                    remaining = Math.max(remaining, duration - toaster.getCookingProgress()[slot]);
                }
            }
        }
        CompoundTag state = station.state().data();
        if (total != state.getInt(ToasterStation.TOTAL_TICKS) || remaining != state.getInt(ToasterStation.REMAINING_TICKS)) {
            CompoundTag timer = new CompoundTag();
            timer.putInt(ToasterStation.TOTAL_TICKS, total);
            timer.putInt(ToasterStation.REMAINING_TICKS, remaining);
            station.state().setData(timer);
        }
    }

    private static void playSound(StationContext station, SoundEvent sound) {
        station.level().playSound(null, station.pos(), sound, SoundSource.BLOCKS, 1, 1);
    }
}
