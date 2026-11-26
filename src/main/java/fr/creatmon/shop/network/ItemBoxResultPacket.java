package fr.creatmon.shop.network;

import fr.creatmon.shop.client.ItemBoxScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record ItemBoxResultPacket(
    List<String>  allItemIds,
    List<Integer> allQuantities,
    List<Integer> allRarities,
    int           newKeyCount
) implements CustomPacketPayload {

    public static final Type<ItemBoxResultPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "itembox_result"));

    public static final StreamCodec<FriendlyByteBuf, ItemBoxResultPacket> CODEC =
        StreamCodec.of(ItemBoxResultPacket::encode, ItemBoxResultPacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, ItemBoxResultPacket pkt) {
        buf.writeInt(pkt.allItemIds().size());
        for (String s : pkt.allItemIds())    buf.writeUtf(s);
        for (int q : pkt.allQuantities())    buf.writeInt(q);
        for (int r : pkt.allRarities())      buf.writeInt(r);
        buf.writeInt(pkt.newKeyCount());
    }

    private static ItemBoxResultPacket decode(FriendlyByteBuf buf) {
        int n = buf.readInt();
        List<String>  ids = new ArrayList<>(n);
        List<Integer> qty = new ArrayList<>(n);
        List<Integer> rar = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add(buf.readUtf());
        for (int i = 0; i < n; i++) qty.add(buf.readInt());
        for (int i = 0; i < n; i++) rar.add(buf.readInt());
        return new ItemBoxResultPacket(ids, qty, rar, buf.readInt());
    }

    public static void handle(ItemBoxResultPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof ItemBoxScreen screen) {
                screen.onResult(pkt);
            }
        });
    }
}
