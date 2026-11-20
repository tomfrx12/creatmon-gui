package fr.creatmon.shop;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(CreatmonShop.MOD_ID)
public class CreatmonShop {

    public static final String MOD_ID = "creatmon_gui";

    public CreatmonShop(IEventBus modEventBus) {
        ModMenus.register(modEventBus);
        modEventBus.addListener(this::setup);
    }

    private void setup(FMLCommonSetupEvent event) {
        ShopData.load();
    }
}
