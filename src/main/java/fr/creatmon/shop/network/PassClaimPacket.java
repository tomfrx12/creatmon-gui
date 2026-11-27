package fr.creatmon.shop.network;

import fr.creatmon.shop.pass.PassData;
import fr.creatmon.shop.pass.PassEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → serveur : récupérer la récompense d'un niveau (level &lt;= 0 = tout). */
public record PassClaimPacket(String track, int level) implements CustomPacketPayload {

    public static final Type<PassClaimPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "pass_claim"));

    public static final StreamCodec<FriendlyByteBuf, PassClaimPacket> CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeUtf(p.track()); buf.writeVarInt(p.level()); },
        buf -> new PassClaimPacket(buf.readUtf(), buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PassClaimPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            PassData.claim(player, pkt.track(), pkt.level());
            PassEvents.sendSync(player, false, null);
        });
    }
}
