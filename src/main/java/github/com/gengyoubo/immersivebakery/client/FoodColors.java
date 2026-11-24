package github.com.gengyoubo.immersivebakery.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The colours of liquids and batters, read from the icons of what they come in without its glass or metal. */
final class FoodColors {
    static final int FALLBACK = 0xE8DCC0;
    private static final int WATER = 0x3F76E4;
    private static final int MILK = 0xF6F3EA;
    private static final Map<Item, Integer> CACHE = new HashMap<>();
    private static BakedModel readWith;

    private FoodColors() {
    }

    static int liquid(ItemStack full) {
        if (full.is(Items.WATER_BUCKET)) {
            return WATER;
        }
        return full.is(Items.MILK_BUCKET) ? MILK : batter(full, full.getCraftingRemainingItem());
    }

    static int batter(ItemStack result, ItemStack container) {
        BakedModel missing = Minecraft.getInstance().getModelManager().getMissingModel();
        if (missing != readWith) {
            // Resources were reloaded.
            readWith = missing;
            CACHE.clear();
        }
        return CACHE.computeIfAbsent(result.getItem(), item -> sample(result, container));
    }

    private static int sample(ItemStack stack, ItemStack container) {
        Set<Integer> leftOut = new HashSet<>();
        if (!container.isEmpty()) {
            pixels(sprite(container)).forEach(leftOut::add);
        }
        int[] pixels = pixels(sprite(stack)).filter(pixel -> !leftOut.contains(pixel)).toArray();
        if (pixels.length == 0) {
            pixels = pixels(sprite(stack)).toArray();
        }
        long red = 0;
        long green = 0;
        long blue = 0;
        long weight = 0;
        for (int abgr : pixels) {
            int r = abgr & 0xFF;
            int g = abgr >> 8 & 0xFF;
            int b = abgr >> 16 & 0xFF;
            // Outlines say little about the food itself.
            int brightness = Math.max(r, Math.max(g, b));
            if (brightness > 90) {
                red += (long) r * brightness;
                green += (long) g * brightness;
                blue += (long) b * brightness;
                weight += brightness;
            }
        }
        return weight == 0 ? FALLBACK : (int) (red / weight) << 16 | (int) (green / weight) << 8 | (int) (blue / weight);
    }

    private static TextureAtlasSprite sprite(ItemStack stack) {
        return Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0).getParticleIcon();
    }

    private static IntStream pixels(TextureAtlasSprite sprite) {
        int width = sprite.contents().width();
        return IntStream.range(0, width * sprite.contents().height())
                .map(index -> sprite.getPixelRGBA(0, index % width, index / width)).filter(abgr -> abgr >>> 24 >= 128);
    }
}
