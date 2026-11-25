package github.com.gengyoubo.immersivebakery.mixin;

import github.com.gengyoubo.immersivebakery.api.player.PlayerImmersionPoses;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HumanoidModel.class, remap = false)
abstract class HumanoidModelMixin {
    @Inject(method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
            at = @At("TAIL"))
    private void immersiveBakery$poseWorkingArms(LivingEntity entity, float swing, float swingAmount, float age, float headYaw, float headPitch,
                                                  CallbackInfo ci) {
        PlayerImmersionPoses.apply((HumanoidModel<?>) (Object) this, entity);
    }
}
