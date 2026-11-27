package fr.creatmon.shop.pass;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.creatmon.shop.network.XpTablePacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** Construit le tableau des valeurs d'XP envoyé au GUI « Valeurs XP ». */
public class XpTable {

    @SuppressWarnings("unchecked")
    public static void send(ServerPlayer player) {
        // ── Pokémon ──
        List<XpTablePacket.PokeEntry> poke = new ArrayList<>();
        try {
            poke = (List<XpTablePacket.PokeEntry>) Class.forName("fr.creatmon.shop.pass.PassCobblemonHook")
                .getMethod("speciesEntries").invoke(null);
        } catch (Throwable ignored) {}

        List<String> pNotes = new ArrayList<>();
        PassConfig.Track cob = PassConfig.get(PassConfig.COBBLEMON);
        if (cob != null) {
            JsonObject cap = cob.xpRule("capture"), ch = cob.xpRule("chase_objective");
            pNotes.add("XP de capture = XP de la rareté + niveau × " + num(cap, "per_level", 2)
                + "   •   Shiny : × " + num(cap, "shiny_multiplier", 3));
            pNotes.add("Quête /chasse : + Commune " + num(ch, "COMMON", 120) + " · Peu commune " + num(ch, "UNCOMMON", 250)
                + " · Rare " + num(ch, "RARE", 500) + " · Ultra-rare " + num(ch, "LEGENDARY", 1000));
        }

        // ── Create ──
        List<XpTablePacket.ItemEntry> items = new ArrayList<>();
        List<String> cNotes = new ArrayList<>();
        double machine = 0.5;
        PassConfig.Track cre = PassConfig.get(PassConfig.CREATE);
        if (cre != null) {
            JsonObject ci = cre.xpRule("craft_item"), at = cre.xpRule("atelier_objective");
            machine = ci.has("machine_multiplier") ? ci.get("machine_multiplier").getAsDouble() : 0.5;
            JsonObject map = ci.has("items") ? ci.getAsJsonObject("items") : new JsonObject();
            Set<String> listed = new HashSet<>();
            Map<String, Double> wildcards = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : map.entrySet()) {
                if (e.getKey().endsWith(":*")) wildcards.put(e.getKey().substring(0, e.getKey().length() - 2), e.getValue().getAsDouble());
                else if (e.getValue().getAsDouble() > 0) { items.add(new XpTablePacket.ItemEntry(e.getKey(), e.getValue().getAsDouble())); listed.add(e.getKey()); }
            }
            // Les jokers "mod:*" sont déployés sur tous les items du mod
            if (!wildcards.isEmpty())
                for (var id : BuiltInRegistries.ITEM.keySet()) {
                    Double xp = wildcards.get(id.getNamespace());
                    if (xp != null && xp > 0 && !listed.contains(id.toString()))
                        items.add(new XpTablePacket.ItemEntry(id.toString(), xp));
                }
            double def = ci.has("default") ? ci.get("default").getAsDouble() : 0;
            cNotes.add("XP par item fabriqué à la table de craft  •  Vos machines Create : × " + fmt(machine)
                + (def > 0 ? "  •  Autres items : " + fmt(def) : ""));
            cNotes.add("Quête /atelier : + Simple " + num(at, "SIMPLE", 120) + " · Modéré " + num(at, "MODERE", 300)
                + " · Complexe " + num(at, "COMPLEXE", 600) + " · Expert " + num(at, "EXPERT", 1200));
        }

        PacketDistributor.sendToPlayer(player, new XpTablePacket(poke, pNotes, items, machine, cNotes));
    }

    private static String num(JsonObject o, String k, double def) {
        return fmt(o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : def);
    }

    static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
