package fr.creatmon.shop.network;

import fr.creatmon.shop.client.PassScreen;
import fr.creatmon.shop.pass.PassConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record PassSyncPacket(List<TrackState> tracks, boolean open, String focusTrack)
        implements CustomPacketPayload {

    public record TrackState(String id, String name, int maxLevel,
                             int level, int xpInLevel, int xpForNext, int totalXp,
                             List<PassConfig.Reward> rewards, Set<Integer> claimed) {}

    public static final Type<PassSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "pass_sync"));

    public static final StreamCodec<FriendlyByteBuf, PassSyncPacket> CODEC =
        StreamCodec.of(PassSyncPacket::encode, PassSyncPacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, PassSyncPacket pkt) {
        buf.writeBoolean(pkt.open());
        buf.writeUtf(pkt.focusTrack());
        writeTracks(buf, pkt.tracks());
    }

    public static void writeTracks(FriendlyByteBuf buf, List<TrackState> tracks) {
        buf.writeVarInt(tracks.size());
        for (TrackState t : tracks) {
            buf.writeUtf(t.id());
            buf.writeUtf(t.name());
            buf.writeVarInt(t.maxLevel());
            buf.writeVarInt(t.level());
            buf.writeVarInt(t.xpInLevel());
            buf.writeVarInt(t.xpForNext());
            buf.writeVarInt(t.totalXp());
            buf.writeVarInt(t.rewards().size());
            for (PassConfig.Reward r : t.rewards()) {
                buf.writeVarInt(r.level());
                buf.writeUtf(r.item());
                buf.writeVarInt(r.count());
            }
            buf.writeVarInt(t.claimed().size());
            for (int c : t.claimed()) buf.writeVarInt(c);
        }
    }

    private static PassSyncPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        String focus = buf.readUtf();
        return new PassSyncPacket(readTracks(buf), open, focus);
    }

    public static List<TrackState> readTracks(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<TrackState> tracks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf(), name = buf.readUtf();
            int max = buf.readVarInt(), lvl = buf.readVarInt();
            int xpIn = buf.readVarInt(), xpNext = buf.readVarInt(), total = buf.readVarInt();
            int rc = buf.readVarInt();
            List<PassConfig.Reward> rewards = new ArrayList<>(rc);
            for (int j = 0; j < rc; j++)
                rewards.add(new PassConfig.Reward(buf.readVarInt(), buf.readUtf(), buf.readVarInt()));
            int cc = buf.readVarInt();
            Set<Integer> claimed = new HashSet<>();
            for (int j = 0; j < cc; j++) claimed.add(buf.readVarInt());
            tracks.add(new TrackState(id, name, max, lvl, xpIn, xpNext, total, rewards, claimed));
        }
        return tracks;
    }

    public static void handle(PassSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof PassScreen screen) screen.onSync(pkt);
            else if (mc.screen instanceof fr.creatmon.shop.client.MenuScreen menu && !pkt.open()) menu.onPassSync(pkt.tracks());
            else if (pkt.open()) mc.setScreen(new PassScreen(pkt));
        });
    }
}
