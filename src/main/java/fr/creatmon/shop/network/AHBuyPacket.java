package fr.creatmon.shop.network;

import fr.creatmon.shop.auction.AuctionData;
import fr.creatmon.shop.auction.AuctionMenu;
import fr.creatmon.shop.auction.AuctionOpener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record AHBuyPacket(UUID listingId) implements CustomPacketPayload {

    public static final Type<AHBuyPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "ah_buy"));

    public static final StreamCodec<FriendlyByteBuf, AHBuyPacket> CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8,
            p -> p.listingId().toString(),
            s -> new AHBuyPacket(UUID.fromString(s)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AHBuyPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            var listing = AuctionData.findListing(pkt.listingId()).orElse(null);
            if (listing == null) {
                player.sendSystemMessage(Component.literal("§cCette annonce n'existe plus."));
                AuctionOpener.sendSync(player);
                return;
            }
            if (listing.sellerUUID().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas acheter votre propre annonce."));
                return;
            }

            int balance = player.getPersistentData().getInt("cinet_balance");
            int cost    = (int) Math.min(listing.price(), Integer.MAX_VALUE);
            if (balance < cost) {
                player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1.0f);
                player.sendSystemMessage(Component.literal("§cSolde insuffisant."));
                return;
            }

            // Débit acheteur
            int newBalance = balance - cost;
            player.getPersistentData().putInt("cinet_balance", newBalance);

            // Item → inbox acheteur
            AuctionData.addToInbox(player.getUUID(), listing.toItemStack());

            // Crédit vendeur
            var server       = player.getServer();
            var sellerOnline = server != null ? server.getPlayerList().getPlayer(listing.sellerUUID()) : null;
            if (sellerOnline != null) {
                int sb = sellerOnline.getPersistentData().getInt("cinet_balance");
                sellerOnline.getPersistentData().putInt("cinet_balance", sb + cost);
                if (sellerOnline.containerMenu instanceof AuctionMenu m) {
                    m.updateBalance(sb + cost);
                    m.broadcastChanges();
                }
                BalanceUtils.sendSync(sellerOnline);
                sellerOnline.sendSystemMessage(Component.literal(
                    "§a[AH] §f" + player.getName().getString() + " a acheté §e" +
                    listing.sellerName() + " §7→ §e+" + cost + " €"));
            } else {
                AuctionData.addPendingBalance(listing.sellerUUID(), cost);
            }

            // Historique prix
            String itemId = BuiltInRegistries.ITEM.getKey(listing.toItemStack().getItem()).toString();
            AuctionData.recordSale(itemId, listing.price());

            // Suppression annonce
            AuctionData.removeListing(listing.id());

            // Sync balance acheteur
            if (player.containerMenu instanceof AuctionMenu m) {
                m.updateBalance(newBalance);
                m.broadcastChanges();
            }

            BalanceUtils.sendSync(player);
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.2f);
            player.sendSystemMessage(Component.literal(
                "§aAchat: §f" + listing.toItemStack().getHoverName().getString() +
                " ×" + listing.quantity() + " §7(§e-" + cost + " €§7) → coffre /ah"));

            AuctionOpener.sendSync(player);
        });
    }
}
