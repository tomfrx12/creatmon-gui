package fr.creatmon.shop.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class BalanceUtils {

    public static void sendSync(ServerPlayer player) {
        int bal = player.getPersistentData().getInt("cinet_balance");
        PacketDistributor.sendToPlayer(player, new BalanceSyncPacket(bal));
    }
}
