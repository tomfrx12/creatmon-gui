package fr.creatmon.shop.network;

import fr.creatmon.shop.client.ClientBalance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BalanceSyncPacket(int balance) implements CustomPacketPayload {

    public static final Type<BalanceSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "balance_sync"));

    public static final StreamCodec<FriendlyByteBuf, BalanceSyncPacket> CODEC =
        StreamCodec.of((buf, pkt) -> buf.writeInt(pkt.balance()), buf -> new BalanceSyncPacket(buf.readInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BalanceSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientBalance.value       = pkt.balance();
            ClientBalance.initialized = true;
        });
    }
}
