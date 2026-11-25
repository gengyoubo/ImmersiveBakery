package github.com.gengyoubo.immersivebakery;

import github.com.gengyoubo.immersivebakery.blender.BlenderStation;
import github.com.gengyoubo.immersivebakery.box.CakeBoxStation;
import github.com.gengyoubo.immersivebakery.bread.BreadStation;
import github.com.gengyoubo.immersivebakery.cup.CupStation;
import github.com.gengyoubo.immersivebakery.dough.DoughStation;
import github.com.gengyoubo.immersivebakery.fermentation.FermentationStation;
import github.com.gengyoubo.immersivebakery.kiln.KilnStation;
import github.com.gengyoubo.immersivebakery.moka.MokaPotStation;
import github.com.gengyoubo.immersivebakery.oven.OvenStation;
import github.com.gengyoubo.immersivebakery.rack.BreadRackStation;
import github.com.gengyoubo.immersivebakery.tank.TankStation;
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
        BreadRackStation.register();
        MokaPotStation.register();
        KilnStation.register();
        DoughStation.register();
        TankStation.register();
        CupStation.register();
        BreadStation.register();
        CakeBoxStation.register();
    }
}
