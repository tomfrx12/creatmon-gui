package fr.creatmon.shop.auction;

import fr.creatmon.shop.network.AHSyncPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AuctionOpener {

    private static final String BALANCE_KEY = "cinet_balance";

    public static void openFor(ServerPlayer player) {
        // Rembourse le solde en attente (ventes pendant l'absence)
        int pending = AuctionData.takePendingBalance(player.getUUID());
        if (pending > 0) {
            int bal = player.getPersistentData().getInt(BALANCE_KEY);
            player.getPersistentData().putInt(BALANCE_KEY, bal + pending);
            player.sendSystemMessage(Component.literal(
                "§a[AH] Revenus perçus pendant votre absence: §e+" + pending + " €"));
        }

        AuctionData.pruneExpired();
        int balance = player.getPersistentData().getInt(BALANCE_KEY);
        fr.creatmon.shop.network.BalanceUtils.sendSync(player);

        List<AHListingData> listings   = buildListingData();
        List<ItemStack>     inbox      = AuctionData.getInboxItems(player.getUUID());
        Map<String, Double> avgPrices  = AuctionData.getAllAvgPrices();

        player.openMenu(
            new SimpleMenuProvider(
                (id, inv, p) -> new AuctionMenu(id, inv, balance),
                Component.empty()),
            buf -> {
                var rb = (RegistryFriendlyByteBuf) buf;
                rb.writeInt(balance);
                rb.writeInt(listings.size());
                for (var l : listings) {
                    rb.writeUUID(l.id());
                    rb.writeUUID(l.sellerUUID());
                    rb.writeUtf(l.sellerName());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(rb, l.item());
                    rb.writeInt(l.quantity());
                    rb.writeLong(l.price());
                    rb.writeLong(l.listedAt());
                }
                rb.writeInt(inbox.size());
                for (var item : inbox) ItemStack.OPTIONAL_STREAM_CODEC.encode(rb, item);
                rb.writeInt(avgPrices.size());
                for (var e : avgPrices.entrySet()) {
                    rb.writeUtf(e.getKey());
                    rb.writeDouble(e.getValue());
                }
            });
    }

    public static void sendSync(ServerPlayer player) {
        AuctionData.pruneExpired();
        PacketDistributor.sendToPlayer(player, new AHSyncPacket(
            buildListingData(),
            AuctionData.getInboxItems(player.getUUID()),
            AuctionData.getAllAvgPrices()));
    }

    public static List<AHListingData> buildListingData() {
        return AuctionData.LISTINGS.stream()
            .map(l -> new AHListingData(l.id(), l.sellerUUID(), l.sellerName(),
                l.toItemStack(), l.quantity(), l.price(), l.listedAt()))
            .collect(Collectors.toList());
    }
}
