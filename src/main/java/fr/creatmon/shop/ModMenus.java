package fr.creatmon.shop;

import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

public class ModMenus {

    static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, CreatmonShop.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ShopMenu>> SHOP_MENU =
            MENU_TYPES.register("shop", () -> IMenuTypeExtension.create(ShopMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<fr.creatmon.shop.auction.AuctionMenu>> AUCTION_MENU =
            MENU_TYPES.register("auction", () -> IMenuTypeExtension.create(fr.creatmon.shop.auction.AuctionMenu::new));

    static void register(IEventBus bus) {
        MENU_TYPES.register(bus);
    }
}
