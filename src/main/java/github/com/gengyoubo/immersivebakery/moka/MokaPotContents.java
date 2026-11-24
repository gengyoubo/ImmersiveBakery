package github.com.gengyoubo.immersivebakery.moka;

import net.minecraftforge.items.ItemStackHandler;

/** The pot's inventory, which Bakeries keeps to itself; implemented by the block entity mixin. */
public interface MokaPotContents {
    ItemStackHandler immersiveBakery$inventory();
}
