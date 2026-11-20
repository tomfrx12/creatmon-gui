package fr.creatmon.shop.network;

import fr.creatmon.shop.craft.CraftTarget;
import fr.creatmon.shop.client.CraftScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record CraftSyncPacket(List<CraftTarget> targets,
                               Set<String>       completed,
                               long              timeRemainingMs) implements CustomPacketPayload {

    public static final Type<CraftSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "craft_sync"));

    public static final StreamCodec<FriendlyByteBuf, CraftSyncPacket> CODEC =
        StreamCodec.of(CraftSyncPacket::encode, CraftSyncPacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, CraftSyncPacket pkt) {
        buf.writeLong(pkt.timeRemainingMs());
        buf.writeInt(pkt.targets().size());
        for (CraftTarget t : pkt.targets()) {
            buf.writeUtf(t.itemId());
            buf.writeInt(t.quantity());
            buf.writeInt(t.difficulty().ordinal());
        }
        buf.writeInt(pkt.completed().size());
        for (String s : pkt.completed()) buf.writeUtf(s);
    }

    private static CraftSyncPacket decode(FriendlyByteBuf buf) {
        long remaining = buf.readLong();
        int n = buf.readInt();
        List<CraftTarget> targets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String itemId   = buf.readUtf();
            int    quantity = buf.readInt();
            CraftTarget.Difficulty diff = CraftTarget.Difficulty.values()[buf.readInt()];
            targets.add(new CraftTarget(itemId, quantity, diff));
        }
        int m = buf.readInt();
        Set<String> completed = new HashSet<>();
        for (int i = 0; i < m; i++) completed.add(buf.readUtf());
        return new CraftSyncPacket(targets, completed, remaining);
    }

    public static void handle(CraftSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof CraftScreen screen) {
                screen.onSync(pkt);
            } else {
                mc.setScreen(new CraftScreen(pkt));
            }
        });
    }
}
