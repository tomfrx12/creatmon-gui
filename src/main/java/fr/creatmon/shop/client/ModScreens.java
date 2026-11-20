package fr.creatmon.shop.client;

import fr.creatmon.shop.CreatmonShop;
import fr.creatmon.shop.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = CreatmonShop.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModScreens {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SHOP_MENU.get(),    ShopScreen::new);
        event.register(ModMenus.AUCTION_MENU.get(), AuctionScreen::new);
    }
}
