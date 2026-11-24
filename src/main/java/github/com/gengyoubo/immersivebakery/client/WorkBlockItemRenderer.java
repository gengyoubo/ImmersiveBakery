package github.com.gengyoubo.immersivebakery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class WorkBlockItemRenderer {
    public static final String IS_BLOCK_MODEL = "Lcom/renyigesai/bakeries/util/measurer/ClientUtilsMeasurer;"
            + "isItem3D(Lnet/minecraft/world/item/ItemStack;)Z";

    private WorkBlockItemRenderer() {
    }

    public static boolean renderThreeDimensionalItem(ItemStack stack, BlockEntity block, PoseStack pose,
                                                       MultiBufferSource buffers, int overlay, int seed) {
        var level = block.getLevel();
        if (level == null || stack.getItem() instanceof BlockItem) {
            return false;
        }

        var renderer = Minecraft.getInstance().getItemRenderer();
        var model = renderer.getModel(stack, level, null, seed);
        if (!model.isGui3d()) {
            return false;
        }

        // Bakeries pitches these models like sprites because they are not BlockItems.
        // https://github.com/Renyigesai/bakery/issues/18
        int light = LevelRenderer.getLightColor(level, block.getBlockPos());
        renderer.render(stack, ItemDisplayContext.FIXED, false, pose, buffers, light, overlay, model);
        return true;
    }
}
