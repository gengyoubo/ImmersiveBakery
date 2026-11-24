package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import github.com.gengyoubo.immersivebakery.ImmersiveBakery;
import github.com.gengyoubo.immersivebakery.kiln.Peel;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The peel's own model, which hands over to {@link PeelRenderer} while something lies on the peel. */
@Mod.EventBusSubscriber(modid = ImmersiveBakery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
final class PeelModel extends BakedModelWrapper<BakedModel> {
    private static final ModelResourceLocation LOCATION = new ModelResourceLocation("bakeries", "stone_kiln_shovel", "inventory");
    private final ItemOverrides overrides;

    private PeelModel(BakedModel empty) {
        super(empty);
        BakedModel loaded = new Loaded(empty);
        overrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                return Peel.load(stack).isEmpty() ? empty.getOverrides().resolve(empty, stack, level, entity, seed) : loaded;
            }
        };
    }

    @SubscribeEvent
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        event.getModels().computeIfPresent(LOCATION, (location, model) -> new PeelModel(model));
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    /** Drawn by the item's custom renderer, in the display positions of the empty peel. */
    static final class Loaded extends BakedModelWrapper<BakedModel> {
        private Loaded(BakedModel empty) {
            super(empty);
        }

        BakedModel empty() {
            return originalModel;
        }

        @Override
        public boolean isCustomRenderer() {
            return true;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            originalModel.applyTransform(context, pose, leftHand);
            return this;
        }
    }
}
