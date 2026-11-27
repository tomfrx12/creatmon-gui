package fr.creatmon.shop.network;

import fr.creatmon.shop.client.MenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Serveur → client : ouvre le /menu. buttonCommands suit l'ordre de MenuConfig.BUTTONS. */
public record MenuSyncPacket(String grade, int prestige, long playSeconds,
                             List<String> buttonCommands,
                             List<PassSyncPacket.TrackState> tracks,
                             List<String> howToXp) implements CustomPacketPayload {

    public static final Type<MenuSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "menu_sync"));

    public static final StreamCodec<FriendlyByteBuf, MenuSyncPacket> CODEC =
        StreamCodec.of(MenuSyncPacket::encode, MenuSyncPacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, MenuSyncPacket p) {
        buf.writeUtf(p.grade());
        buf.writeVarInt(p.prestige());
        buf.writeVarLong(p.playSeconds());
        writeStrings(buf, p.buttonCommands());
        PassSyncPacket.writeTracks(buf, p.tracks());
        writeStrings(buf, p.howToXp());
    }

    private static MenuSyncPacket decode(FriendlyByteBuf buf) {
        return new MenuSyncPacket(buf.readUtf(), buf.readVarInt(), buf.readVarLong(),
            readStrings(buf), PassSyncPacket.readTracks(buf), readStrings(buf));
    }

    private static void writeStrings(FriendlyByteBuf buf, List<String> l) {
        buf.writeVarInt(l.size());
        for (String s : l) buf.writeUtf(s);
    }

    private static List<String> readStrings(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(buf.readUtf());
        return l;
    }

    public static void handle(MenuSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> Minecraft.getInstance().setScreen(new MenuScreen(pkt)));
    }
}
