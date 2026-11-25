package github.com.gengyoubo.immersivebakery.mixin;

import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationHost;
import com.pockyl.diegesis.api.state.StationState;
import com.renyigesai.bakeries.block.bread_basket.BreadBasketBlock;
import com.renyigesai.bakeries.block.bread_basket.BreadBasketBlockEntity;
import github.com.gengyoubo.immersivebakery.basket.BasketMenuSlots;
import github.com.gengyoubo.immersivebakery.basket.BreadBasketStation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The basket holds what there is room for, remembers where it lies, and tells the clients, so that they see what is in it. */
@Mixin(value = BreadBasketBlockEntity.class, remap = false)
abstract class BreadBasketBlockEntityMixin extends RandomizableContainerBlockEntity implements StationHost {
    @Unique private static final String immersiveBakery$STATION = "immersive_bakery:station";

    @Shadow private NonNullList<ItemStack> items;
    @Unique private StationState immersiveBakery$station;
    /** What the clients were told. */
    @Unique private NonNullList<ItemStack> immersiveBakery$shown;

    private BreadBasketBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void immersiveBakery$makeRoom(BlockPos pos, BlockState state, CallbackInfo ci) {
        items = NonNullList.withSize(BreadBasketStation.SLOT_COUNT, ItemStack.EMPTY);
    }

    @Inject(method = {"getContainerSize", "m_6643_"}, at = @At("HEAD"), cancellable = true)
    private void immersiveBakery$getSize(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(BreadBasketStation.SLOT_COUNT);
    }

    @Inject(method = {"createMenu", "m_6555_"}, at = @At("HEAD"), cancellable = true)
    private void immersiveBakery$showFirstSlots(int id, Inventory inventory, CallbackInfoReturnable<AbstractContainerMenu> cir) {
        cir.setReturnValue(BasketMenuSlots.menu(id, inventory, this));
    }

    // What is in the basket is seen, not the same loaves whatever it is.
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void immersiveBakery$showItems(Level level, BlockPos pos, BlockState state, BreadBasketBlockEntity basket, CallbackInfo ci) {
        ci.cancel();
        if (state.getValue(BreadBasketBlock.FILL)) {
            level.setBlock(pos, state.setValue(BreadBasketBlock.FILL, false), Block.UPDATE_ALL);
        }
        ((BreadBasketBlockEntityMixin) (Object) basket).immersiveBakery$tellClients();
    }

    @Unique
    private void immersiveBakery$tellClients() {
        boolean known = immersiveBakery$shown != null;
        if (!known) {
            immersiveBakery$shown = NonNullList.withSize(BreadBasketStation.SLOT_COUNT, ItemStack.EMPTY);
        }
        boolean changed = false;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!ItemStack.matches(stack, immersiveBakery$shown.get(i))) {
                if (stack.isEmpty()) {
                    immersiveBakery$forgetPlace(i);
                }
                immersiveBakery$shown.set(i, stack.copy());
                changed = true;
            }
        }
        if (changed && known) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Of what a hopper took, and of what lay on it, which is set out anew. */
    @Unique
    private void immersiveBakery$forgetPlace(int slot) {
        SlotRef ref = SlotRef.of(BreadBasketStation.BREADS, slot);
        if (stationState().getPose(ref) == null) {
            return;
        }
        stationState().setPose(ref, null);
        for (int above = 0; above < BreadBasketStation.SLOT_COUNT; above++) {
            ItemPose pose = stationState().getPose(SlotRef.of(BreadBasketStation.BREADS, above));
            if (pose != null && pose.on() == slot) {
                immersiveBakery$forgetPlace(above);
            }
        }
    }

    @Override
    public StationState stationState() {
        if (immersiveBakery$station == null) {
            immersiveBakery$station = new StationState(BreadBasketStation.SCHEMA);
        }
        return immersiveBakery$station;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Inject(method = {"saveAdditional", "m_183515_"}, at = @At("TAIL"))
    private void immersiveBakery$savePlaces(CompoundTag tag, CallbackInfo ci) {
        tag.put(immersiveBakery$STATION, stationState().save());
    }

    @Inject(method = {"load", "m_142466_"}, at = @At("TAIL"))
    private void immersiveBakery$loadPlaces(CompoundTag tag, CallbackInfo ci) {
        stationState().load(tag.getCompound(immersiveBakery$STATION));
    }
}
