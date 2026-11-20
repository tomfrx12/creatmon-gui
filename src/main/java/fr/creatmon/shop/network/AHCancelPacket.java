package fr.creatmon.shop.network;

import fr.creatmon.shop.auction.AuctionData;
import fr.creatmon.shop.auction.AuctionOpener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record AHCancelPacket(UUID listingId) implements CustomPacketPayload {

    public static final Type<AHCancelPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "ah_cancel"));

    public static final StreamCodec<FriendlyByteBuf, AHCancelPacket> CODEC =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8,
            p -> p.listingId().toString(),
            s -> new AHCancelPacket(UUID.fromString(s)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AHCancelPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            var listing = AuctionData.findListing(pkt.listingId()).orElse(null);
            if (listing == null || !listing.sellerUUID().equals(player.getUUID())) return;

            AuctionData.addToInbox(listing.sellerUUID(), listing.toItemStack());
            AuctionData.removeListing(listing.id());

            player.sendSystemMessage(Component.literal(
                "§aAnnonce annulée: §f" + listing.toItemStack().getHoverName().getString() +
                " ×" + listing.quantity() + " → coffre /ah"));

            AuctionOpener.sendSync(player);
        });
    }
}
