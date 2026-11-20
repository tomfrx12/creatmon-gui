package fr.creatmon.shop.network;

import fr.creatmon.shop.auction.AHListingData;
import fr.creatmon.shop.auction.AuctionMenu;
import fr.creatmon.shop.client.AuctionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record AHSyncPacket(List<AHListingData> listings,
                            List<ItemStack>     inboxItems,
                            Map<String, Double> avgPrices) implements CustomPacketPayload {

    public static final Type<AHSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "ah_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AHSyncPacket> CODEC =
        StreamCodec.of(AHSyncPacket::encode, AHSyncPacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // ── Codec ─────────────────────────────────────────────────────────

    private static void encode(RegistryFriendlyByteBuf buf, AHSyncPacket pkt) {
        buf.writeInt(pkt.listings().size());
        for (var l : pkt.listings()) {
            buf.writeUUID(l.id());
            buf.writeUUID(l.sellerUUID());
            buf.writeUtf(l.sellerName());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, l.item());
            buf.writeInt(l.quantity());
            buf.writeLong(l.price());
            buf.writeLong(l.listedAt());
        }
        buf.writeInt(pkt.inboxItems().size());
        for (var item : pkt.inboxItems())
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, item);
        buf.writeInt(pkt.avgPrices().size());
        for (var e : pkt.avgPrices().entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeDouble(e.getValue());
        }
    }

    private static AHSyncPacket decode(RegistryFriendlyByteBuf buf) {
        int n = buf.readInt();
        List<AHListingData> listings = new ArrayList<>();
        for (int i = 0; i < n; i++)
            listings.add(new AHListingData(
                buf.readUUID(), buf.readUUID(), buf.readUtf(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                buf.readInt(), buf.readLong(), buf.readLong()));
        int m = buf.readInt();
        List<ItemStack> inbox = new ArrayList<>();
        for (int i = 0; i < m; i++) inbox.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        int p = buf.readInt();
        Map<String, Double> avgs = new HashMap<>();
        for (int i = 0; i < p; i++) avgs.put(buf.readUtf(), buf.readDouble());
        return new AHSyncPacket(listings, inbox, avgs);
    }

    // ── Handler (client-side) ─────────────────────────────────────────

    public static void handle(AHSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu instanceof AuctionMenu menu) {
                menu.updateListings(pkt.listings());
                menu.updateInbox(pkt.inboxItems());
                menu.updateAvgPrices(pkt.avgPrices());
            }
            if (mc.screen instanceof AuctionScreen screen)
                screen.onSync();
        });
    }
}
