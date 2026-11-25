package github.com.gengyoubo.immersivebakery.bread;

import com.renyigesai.bakeries.api.block.IKnifeCutBlock;
import com.renyigesai.bakeries.api.block.IMouldBlock;
import com.renyigesai.bakeries.block.CountryBreadBlock;
import com.renyigesai.bakeries.block.MouldCakeBlock;
import com.renyigesai.bakeries.block.MouldToastBlock;
import com.renyigesai.bakeries.block.ToastBlock;
import com.renyigesai.bakeries.block.cake.CakeBaseBlock;
import com.renyigesai.bakeries.block.cake.MultiLayerCreamCakeBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** What a knife does to bread and cake: it loosens them from their mould, and cuts them up. */
public final class BreadCutting {
    private static final TagKey<Item> BREAD_KNIVES = ItemTags.create(ResourceLocation.fromNamespaceAndPath("bakeries", "bread_knife"));
    private static final TagKey<Item> KNIVES = ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "tools/knives"));
    /** Bakeries' bread lies along x of its model from here to there, pixels. */
    private static final float FROM = 4;
    private static final float TO = 12;
    /** Its cakes take up this much both ways, and a cake base is that high. */
    public static final float CAKE_FROM = 3;
    public static final float CAKE_TO = 13;
    private static final float CAKE_HEIGHT = 6;

    private BreadCutting() {
    }

    /** What bread becomes: the block that is left, and what the player gets. */
    public record Result(BlockState left, List<ItemStack> given) {
    }

    /** As Bakeries decides it for its bread. */
    public static boolean isKnife(ItemStack stack) {
        return stack.is(BREAD_KNIVES) || stack.is(KNIVES);
    }

    public static boolean isInMould(BlockState bread) {
        return bread.getBlock() instanceof MouldCakeBlock || bread.getBlock() instanceof MouldToastBlock && bread.getValue(MouldToastBlock.PILE) == 1;
    }

    public static boolean isCuttable(BlockState bread) {
        return (bread.getBlock() instanceof ToastBlock || bread.getBlock() instanceof CountryBreadBlock || isCake(bread))
                && ((IKnifeCutBlock) bread.getBlock()).isCut(bread);
    }

    /** Cake that a knife cuts; without a knife it is for eating. */
    public static boolean isCake(BlockState bread) {
        return isLayered(bread) || bread.getBlock() instanceof MultiLayerCreamCakeBlock;
    }

    /** Larger than bread, whether in its mould or out of it. */
    public static boolean isLarge(BlockState bread) {
        return isCake(bread) || bread.getBlock() instanceof MouldCakeBlock;
    }

    /** It is cut across into layers, not from above. */
    public static boolean isLayered(BlockState bread) {
        return bread.getBlock() instanceof CakeBaseBlock;
    }

    /** How many slices it makes. Toast loses them one after the other, all other bread is cut up as a whole. */
    public static int slices(BlockState bread) {
        return !isCuttable(bread) ? 0 : losesSlices(bread) ? bread.getValue(IKnifeCutBlock.SLICE) : ((IKnifeCutBlock) bread.getBlock()).getSliceItemCount();
    }

    private static int wholeSlices(BlockState bread) {
        return losesSlices(bread) ? 4 : slices(bread);
    }

    private static boolean losesSlices(BlockState bread) {
        return bread.getBlock() instanceof ToastBlock;
    }

    /** The slices beside each other along x; a cake is cut along x as well, for as many more behind them. */
    private static int rows(BlockState bread) {
        return bread.getBlock() instanceof MultiLayerCreamCakeBlock ? 2 : 1;
    }

    /** The lines, from 1, that are still to be cut: between the slices that are left. The first is next to the end that goes first. */
    public static List<Integer> lines(BlockState bread) {
        List<Integer> lines = new ArrayList<>();
        if (rows(bread) > 1) {
            int columns = slices(bread) / rows(bread);
            for (int line = 1; line < columns + rows(bread) - 1; line++) {
                lines.add(line);
            }
            return lines;
        }
        int whole = wholeSlices(bread);
        for (int line = whole - slices(bread) + 1; line < whole; line++) {
            lines.add(line);
        }
        return lines;
    }

    /** Whether a line runs across the bread, along x of its model, rather than away from the player. */
    public static boolean isAcross(BlockState bread, int line) {
        return isLayered(bread) || rows(bread) > 1 && line >= slices(bread) / rows(bread);
    }

    /** Where a line crosses the bread, in pixels along x of its model; along z when it runs across, or up when it divides layers. */
    public static float position(BlockState bread, int line) {
        if (isLayered(bread)) {
            return CAKE_HEIGHT * line / slices(bread);
        }
        if (rows(bread) > 1) {
            int columns = slices(bread) / rows(bread);
            float share = line < columns ? (float) line / columns : (float) (line - columns + 1) / rows(bread);
            return CAKE_FROM + (CAKE_TO - CAKE_FROM) * share;
        }
        return FROM + (TO - FROM) * line / wholeSlices(bread);
    }

    /** The bread out of its mould stands where that stood; the mould goes to the player. */
    public static Result loosen(BlockState mould) {
        IMouldBlock block = (IMouldBlock) mould.getBlock();
        Item content = block.getMouldContentItem().get();
        ItemStack empty = new ItemStack(block.getMouldItem().get());
        if (!(content instanceof BlockItem bread)) {
            return new Result(Blocks.AIR.defaultBlockState(), List.of(empty, new ItemStack(content)));
        }
        BlockState left = bread.getBlock().defaultBlockState();
        if (left.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && mould.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            left = left.setValue(BlockStateProperties.HORIZONTAL_FACING, mould.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }
        return new Result(left, List.of(empty));
    }

    /** What is left when the lines of {@code cut}, a bit each, are cut: every slice that is loose comes off. */
    public static Result cut(BlockState bread, int cut) {
        Item slice = ((IKnifeCutBlock) bread.getBlock()).getSliceItem();
        List<Integer> lines = lines(bread);
        boolean all = lines.stream().allMatch(line -> (cut & 1 << line) != 0);
        if (all) {
            return new Result(Blocks.AIR.defaultBlockState(), List.of(new ItemStack(slice, slices(bread))));
        }
        int loose = 0;
        while (losesSlices(bread) && (cut & 1 << lines.get(loose)) != 0) {
            loose++;
        }
        return loose == 0 ? new Result(bread, List.of())
                : new Result(bread.setValue(IKnifeCutBlock.SLICE, slices(bread) - loose), List.of(new ItemStack(slice, loose)));
    }
}
