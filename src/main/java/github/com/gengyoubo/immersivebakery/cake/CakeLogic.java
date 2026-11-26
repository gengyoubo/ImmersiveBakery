package github.com.gengyoubo.immersivebakery.cake;

import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import com.renyigesai.bakeries.block.custom_cake.CustomCakeBlock;
import com.renyigesai.bakeries.block.custom_cake.CustomCakeBlockEntity;
import com.renyigesai.bakeries.init.BakeriesBlocks;
import com.renyigesai.bakeries.util.measurer.CakePartMeasurer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class CakeLogic implements StationLogic {
    CakeLogic(StationContext station) { }
    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!CakeStation.ADD.equals(action) || !player.mayBuild() || !player.level().mayInteract(player, station.pos())) return;
        ItemStack carried = player.inventoryMenu.getCarried();
        var part = CakePartMeasurer.isIngredient(carried).orElse(null);
        if (part == null || !part.getType().equals(CakeStation.nextPart(station.level().getBlockEntity(station.pos())))) return;
        CustomCakeBlockEntity cake;
        if (station.blockState().is(BakeriesBlocks.SILICONE_PAPER.get())) {
            if (!CakePartMeasurer.CAKE_BASE.equals(part.getType())) return;
            var facing = station.blockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
            station.level().setBlock(station.pos(), BakeriesBlocks.CUSTOM_CAKE.get().defaultBlockState().setValue(CustomCakeBlock.FACING, facing), 3);
            if (!(station.level().getBlockEntity(station.pos()) instanceof CustomCakeBlockEntity created)) return;
            cake = created;
        } else if (station.level().getBlockEntity(station.pos()) instanceof CustomCakeBlockEntity existing && existing.test(part)) {
            cake = existing;
        } else return;
        cake.addCakePart(part);
        if (!player.getAbilities().instabuild) {
            ItemStack remainder = carried.getCraftingRemainingItem();
            carried.shrink(1);
            if (!remainder.isEmpty() && !player.getInventory().add(remainder)) player.drop(remainder, false);
        }
        station.level().playSound(null, station.pos(), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
    }
}
