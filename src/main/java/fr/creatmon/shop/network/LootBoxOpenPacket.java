package fr.creatmon.shop.network;

import fr.creatmon.shop.client.ItemBoxScreen;
import fr.creatmon.shop.client.LootBoxScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LootBoxOpenPacket(String chestType, int keyCount) implements CustomPacketPayload {

    public static final Type<LootBoxOpenPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "lootbox_open"));

    public static final StreamCodec<FriendlyByteBuf, LootBoxOpenPacket> CODEC =
        StreamCodec.of(LootBoxOpenPacket::encode, LootBoxOpenPacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, LootBoxOpenPacket pkt) {
        buf.writeUtf(pkt.chestType());
        buf.writeInt(pkt.keyCount());
    }

    private static LootBoxOpenPacket decode(FriendlyByteBuf buf) {
        return new LootBoxOpenPacket(buf.readUtf(), buf.readInt());
    }

    public static void handle(LootBoxOpenPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if ("poke_ball".equals(pkt.chestType())) {
                if (mc.screen instanceof ItemBoxScreen screen) {
                    screen.onOpen(pkt.keyCount());
                } else {
                    mc.setScreen(new ItemBoxScreen(pkt.chestType(), pkt.keyCount()));
                }
            } else {
                if (mc.screen instanceof LootBoxScreen screen) {
                    screen.onOpen(pkt.keyCount());
                } else {
                    mc.setScreen(new LootBoxScreen(pkt.chestType(), pkt.keyCount()));
                }
            }
        });
    }
}
