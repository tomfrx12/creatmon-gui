package fr.creatmon.shop;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/**
 * Called from KubeJS via Java.loadClass('fr.creatmon.shop.ShopOpener').openFor(player)
 */
public class ShopOpener {

    private static final String BALANCE_KEY = "cinet_balance";

    private static final int STARTING_BALANCE = 2500;

    public static void openFor(ServerPlayer player) {
        if (!player.getPersistentData().contains(BALANCE_KEY)) {
            player.getPersistentData().putInt(BALANCE_KEY, STARTING_BALANCE);
        }
        int balance = player.getPersistentData().getInt(BALANCE_KEY);
        fr.creatmon.shop.network.BalanceUtils.sendSync(player);
        player.openMenu(
            new SimpleMenuProvider(
                (id, inv, p) -> new ShopMenu(id, inv, balance),
                Component.empty()
            ),
            buf -> buf.writeInt(balance)
        );
    }
}
