package fr.creatmon.shop.network;

import fr.creatmon.shop.chase.ChaseTarget;
import fr.creatmon.shop.client.ChaseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record ChaseSyncPacket(List<ChaseTarget> targets,
                               Set<String>       completed,
                               long              timeRemainingMs) implements CustomPacketPayload {

    public static final Type<ChaseSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "chase_sync"));

    public static final StreamCodec<FriendlyByteBuf, ChaseSyncPacket> CODEC =
        StreamCodec.of(ChaseSyncPacket::encode, ChaseSyncPacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, ChaseSyncPacket pkt) {
        buf.writeLong(pkt.timeRemainingMs());
        buf.writeInt(pkt.targets().size());
        for (ChaseTarget t : pkt.targets()) {
            buf.writeUtf(t.speciesName());
            buf.writeInt(t.rarity().ordinal());
        }
        buf.writeInt(pkt.completed().size());
        for (String s : pkt.completed()) buf.writeUtf(s);
    }

    private static ChaseSyncPacket decode(FriendlyByteBuf buf) {
        long remaining = buf.readLong();
        int n = buf.readInt();
        List<ChaseTarget> targets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String species  = buf.readUtf();
            ChaseTarget.Rarity rarity = ChaseTarget.Rarity.values()[buf.readInt()];
            targets.add(new ChaseTarget(species, rarity));
        }
        int m = buf.readInt();
        Set<String> completed = new HashSet<>();
        for (int i = 0; i < m; i++) completed.add(buf.readUtf());
        return new ChaseSyncPacket(targets, completed, remaining);
    }

    public static void handle(ChaseSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof ChaseScreen screen) {
                screen.onSync(pkt);
            } else {
                mc.setScreen(new ChaseScreen(pkt));
            }
        });
    }
}
