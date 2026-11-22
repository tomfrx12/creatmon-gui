package fr.creatmon.shop.network;

import fr.creatmon.shop.client.LootBoxScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record LootBoxResultPacket(
    String       species,
    int          rarityOrdinal,
    List<String> decoys,
    int          newKeyCount,
    List<String>  allSpecies,
    List<Integer> allRarities
) implements CustomPacketPayload {

    public static final Type<LootBoxResultPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "lootbox_result"));

    public static final StreamCodec<FriendlyByteBuf, LootBoxResultPacket> CODEC =
        StreamCodec.of(LootBoxResultPacket::encode, LootBoxResultPacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, LootBoxResultPacket pkt) {
        buf.writeUtf(pkt.species());
        buf.writeInt(pkt.rarityOrdinal());
        buf.writeInt(pkt.decoys().size());
        for (String d : pkt.decoys()) buf.writeUtf(d);
        buf.writeInt(pkt.newKeyCount());
        buf.writeInt(pkt.allSpecies().size());
        for (String s : pkt.allSpecies()) buf.writeUtf(s);
        for (int r : pkt.allRarities()) buf.writeInt(r);
    }

    private static LootBoxResultPacket decode(FriendlyByteBuf buf) {
        String       species  = buf.readUtf();
        int          rarity   = buf.readInt();
        int          n        = buf.readInt();
        List<String> decoys   = new ArrayList<>(n);
        for (int i = 0; i < n; i++) decoys.add(buf.readUtf());
        int          keys     = buf.readInt();
        int          m        = buf.readInt();
        List<String>  all     = new ArrayList<>(m);
        List<Integer> allR    = new ArrayList<>(m);
        for (int i = 0; i < m; i++) all.add(buf.readUtf());
        for (int i = 0; i < m; i++) allR.add(buf.readInt());
        return new LootBoxResultPacket(species, rarity, decoys, keys, all, allR);
    }

    public static void handle(LootBoxResultPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof LootBoxScreen screen) {
                screen.onResult(pkt);
            }
        });
    }
}
