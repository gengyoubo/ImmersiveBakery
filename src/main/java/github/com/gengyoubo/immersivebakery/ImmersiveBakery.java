package github.com.gengyoubo.immersivebakery;

import github.com.gengyoubo.immersivebakery.blender.BlenderStation;
import github.com.gengyoubo.immersivebakery.fermentation.FermentationStation;
import github.com.gengyoubo.immersivebakery.oven.OvenStation;
import github.com.gengyoubo.immersivebakery.toaster.ToasterStation;
import net.minecraftforge.fml.common.Mod;

@Mod(ImmersiveBakery.MOD_ID)
public final class ImmersiveBakery {
    public static final String MOD_ID = "immersive_bakery";

    public ImmersiveBakery() {
        ToasterStation.register();
        OvenStation.register();
        FermentationStation.register();
        BlenderStation.register();
    }
}
