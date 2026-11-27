package fr.creatmon.shop.network;

import fr.creatmon.shop.client.XpTableScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Serveur → client : XP rapportée par chaque Pokémon et chaque item, par métier. */
public record XpTablePacket(List<PokeEntry> pokemon, List<String> pokemonNotes,
                            List<ItemEntry> items, double machineMultiplier,
                            List<String> createNotes) implements CustomPacketPayload {

    /** rarity = ordinal de PassXp.CaptureRarity. */
    public record PokeEntry(int dex, String langKey, String name, int rarity, int baseXp) {}
    public record ItemEntry(String id, double xp) {}

    public static final Type<XpTablePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "xp_table"));

    public static final StreamCodec<FriendlyByteBuf, XpTablePacket> CODEC =
        StreamCodec.of(XpTablePacket::encode, XpTablePacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, XpTablePacket p) {
        list(buf, p.pokemon(), (b, e) -> {
            b.writeVarInt(e.dex()); b.writeUtf(e.langKey()); b.writeUtf(e.name());
            b.writeVarInt(e.rarity()); b.writeVarInt(e.baseXp());
        });
        list(buf, p.pokemonNotes(), FriendlyByteBuf::writeUtf);
        list(buf, p.items(), (b, e) -> { b.writeUtf(e.id()); b.writeDouble(e.xp()); });
        buf.writeDouble(p.machineMultiplier());
        list(buf, p.createNotes(), FriendlyByteBuf::writeUtf);
    }

    private static XpTablePacket decode(FriendlyByteBuf buf) {
        return new XpTablePacket(
            read(buf, b -> new PokeEntry(b.readVarInt(), b.readUtf(), b.readUtf(), b.readVarInt(), b.readVarInt())),
            read(buf, FriendlyByteBuf::readUtf),
            read(buf, b -> new ItemEntry(b.readUtf(), b.readDouble())),
            buf.readDouble(),
            read(buf, FriendlyByteBuf::readUtf));
    }

    private static <T> void list(FriendlyByteBuf buf, List<T> l, BiConsumer<FriendlyByteBuf, T> w) {
        buf.writeVarInt(l.size());
        for (T t : l) w.accept(buf, t);
    }

    private static <T> List<T> read(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> r) {
        int n = buf.readVarInt();
        List<T> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(r.apply(buf));
        return l;
    }

    public static void handle(XpTablePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> Minecraft.getInstance().setScreen(new XpTableScreen(pkt)));
    }
}
