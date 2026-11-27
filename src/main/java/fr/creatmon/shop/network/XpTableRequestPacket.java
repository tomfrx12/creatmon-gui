package fr.creatmon.shop.network;

import fr.creatmon.shop.pass.XpTable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → serveur : demande le tableau des valeurs d'XP. */
public record XpTableRequestPacket() implements CustomPacketPayload {

    public static final Type<XpTableRequestPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "xp_table_request"));

    public static final StreamCodec<FriendlyByteBuf, XpTableRequestPacket> CODEC =
        StreamCodec.of((buf, p) -> {}, buf -> new XpTableRequestPacket());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(XpTableRequestPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) XpTable.send(p);
        });
    }
}
