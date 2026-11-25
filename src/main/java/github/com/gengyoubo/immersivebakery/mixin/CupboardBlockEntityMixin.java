package github.com.gengyoubo.immersivebakery.mixin;

import com.pockyl.diegesis.api.state.StationHost;
import com.pockyl.diegesis.api.state.StationState;
import com.renyigesai.bakeries.block.cupboard.CupboardBlockEntity;
import com.renyigesai.bakeries.block.dough_crafting_table.DoughCraftingTableBlockEntity;
import github.com.gengyoubo.immersivebakery.dough.DoughStation;
import github.com.gengyoubo.immersivebakery.storage.CupboardStation;
import github.com.gengyoubo.immersivebakery.storage.Drawers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
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

/** The drawers have more slots, and remember where what is in them lies. */
@Mixin(value = {CupboardBlockEntity.class, DoughCraftingTableBlockEntity.class}, remap = false)
abstract class CupboardBlockEntityMixin extends RandomizableContainerBlockEntity implements StationHost {
    @Unique private static final String immersiveBakery$STATION = "immersive_bakery:station";

    @Shadow(remap = false) private NonNullList<ItemStack> items;
    @Unique private StationState immersiveBakery$station;

    private CupboardBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void immersiveBakery$makeRoom(BlockPos pos, BlockState state, CallbackInfo ci) {
        items = NonNullList.withSize(Drawers.SLOT_COUNT, ItemStack.EMPTY);
    }

    @Inject(method = {"getContainerSize", "m_6643_"}, at = @At("HEAD"), cancellable = true)
    private void immersiveBakery$getSize(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Drawers.SLOT_COUNT);
    }

    @Override
    public StationState stationState() {
        if (immersiveBakery$station == null) {
            immersiveBakery$station = new StationState((Object) this instanceof DoughCraftingTableBlockEntity ? DoughStation.SCHEMA : CupboardStation.SCHEMA);
        }
        return immersiveBakery$station;
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
