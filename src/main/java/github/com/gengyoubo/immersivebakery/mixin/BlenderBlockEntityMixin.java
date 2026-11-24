package github.com.gengyoubo.immersivebakery.mixin;

import com.pockyl.diegesis.api.Diegesis;
import com.renyigesai.bakeries.block.blender.BlenderBlockEntity;
import com.renyigesai.bakeries.recipe.BlenderRecipe;
import github.com.gengyoubo.immersivebakery.blender.BlenderCrafting;
import github.com.gengyoubo.immersivebakery.blender.BlenderLiquids;
import github.com.gengyoubo.immersivebakery.blender.BlenderPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlenderBlockEntity.class, remap = false)
abstract class BlenderBlockEntityMixin implements BlenderCrafting {
    @Shadow
    private boolean craftItem() {
        throw new AssertionError();
    }

    @Shadow
    private boolean isContainer(BlenderRecipe recipe) {
        throw new AssertionError();
    }

    @Override
    public boolean immersiveBakery$craft() {
        return craftItem();
    }

    @Inject(method = "craftTick", at = @At("HEAD"), cancellable = true)
    private static void immersiveBakery$waitForPower(Level level, BlockPos pos, BlockState state,
                                                     BlenderBlockEntity blender, CallbackInfo ci) {
        if (!BlenderPower.beforeTick(blender)) {
            ci.cancel();
        }
    }

    @Inject(method = "craftTick", at = @At("RETURN"))
    private static void immersiveBakery$finishBatch(Level level, BlockPos pos, BlockState state,
                                                    BlenderBlockEntity blender, CallbackInfo ci) {
        BlenderPower.stopAfterBatch(blender);
    }

    @Redirect(method = "craftItem", at = @At(value = "INVOKE", target =
            "Lcom/renyigesai/bakeries/block/blender/BlenderBlockEntity;isContainer(Lcom/renyigesai/bakeries/recipe/BlenderRecipe;)Z"))
    private boolean immersiveBakery$mixBeforeFilling(BlenderBlockEntity blender, BlenderRecipe recipe) {
        return isContainer(recipe) || BlenderPower.mixesWithoutContainer(blender);
    }

    @Redirect(method = "craftItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;hasCraftingRemainingItem()Z"))
    private boolean immersiveBakery$keepVessel(ItemStack stack) {
        return stack.hasCraftingRemainingItem() && !BlenderLiquids.isStored(stack);
    }

    @Inject(method = "craftItem", at = @At("RETURN"))
    private void immersiveBakery$useWater(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            BlenderLiquids.useWater((BlenderBlockEntity) (Object) this);
        }
    }

    // Only what lies in the bowl flies about, not the bottles and buckets that liquids came in.
    @Inject(method = "spawnParticle", at = @At("HEAD"), cancellable = true)
    private void immersiveBakery$spawnSolidParticles(CallbackInfo ci) {
        var self = (BlenderBlockEntity) (Object) this;
        if (BlenderPower.isPowered(self) && self.getLevel() instanceof ServerLevel level) {
            ci.cancel();
            var center = self.getBlockPos().getCenter();
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = self.getInventory().getStackInSlot(slot);
                if (!stack.isEmpty() && !BlenderLiquids.isStored(stack)) {
                    level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack), center.x, center.y, center.z, 1, 0, 0, 0, 0.075);
                }
            }
        }
    }

    @Inject(method = "drops", at = @At("HEAD"))
    private void immersiveBakery$spillLiquids(BlenderBlockEntity blender, CallbackInfo ci) {
        BlenderLiquids.spill(blender);
    }

    // Emptied buckets and bottles go to whoever works here instead of onto the floor.
    @Inject(method = "ejectIngredientRemainder", at = @At("HEAD"), cancellable = true)
    private void immersiveBakery$handBackRemainder(ItemStack remainder, CallbackInfo ci) {
        var self = (BlenderBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Diegesis.activeStation(level, self.getBlockPos()).flatMap(station -> station.users().stream().findFirst())
                .ifPresent(user -> {
                    if (!user.getInventory().add(remainder)) {
                        user.drop(remainder, false);
                    }
                    ci.cancel();
                });
    }
}
