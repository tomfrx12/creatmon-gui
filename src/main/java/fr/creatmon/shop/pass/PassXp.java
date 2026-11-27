package fr.creatmon.shop.pass;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerPlayer;

/**
 * Calculs d'XP de la voie Cobblemon, entièrement paramétrés dans
 * config/creatmon_pass.json → cobblemon.xp_sources.
 */
public class PassXp {

    public enum CaptureRarity { COMMON, UNCOMMON, RARE, VERY_RARE, LEGENDARY }

    /**
     * Rareté d'un Pokémon : légendaire / mythique / ultra-chimère → LEGENDARY,
     * sinon d'après le taux de capture de l'espèce (3 = très rare … 255 = très commun).
     */
    public static CaptureRarity rarity(int catchRate, boolean legendary, JsonObject rule) {
        if (legendary) return CaptureRarity.LEGENDARY;
        JsonObject th = rule.has("catch_rate_thresholds") ? rule.getAsJsonObject("catch_rate_thresholds") : new JsonObject();
        if (catchRate >= intOr(th, "common", 150))   return CaptureRarity.COMMON;
        if (catchRate >= intOr(th, "uncommon", 75))  return CaptureRarity.UNCOMMON;
        if (catchRate >= intOr(th, "rare", 25))      return CaptureRarity.RARE;
        return CaptureRarity.VERY_RARE;
    }

    /** XP = (XP de base de la rareté + niveau × per_level) × multiplicateur shiny. */
    public static int captureXp(int catchRate, boolean legendary, int level, boolean shiny) {
        PassConfig.Track t = PassConfig.get(PassConfig.COBBLEMON);
        if (t == null) return 0;
        JsonObject rule = t.xpRule("capture");
        CaptureRarity r = rarity(catchRate, legendary, rule);
        double xp = rarityXp(r) + level * doubleOr(rule, "per_level", 2.0);
        if (shiny) xp *= doubleOr(rule, "shiny_multiplier", 3.0);
        return (int) Math.round(xp);
    }

    /** XP de base d'une rareté de capture (hors niveau / shiny). */
    public static int rarityXp(CaptureRarity r) {
        PassConfig.Track t = PassConfig.get(PassConfig.COBBLEMON);
        JsonObject rule = t == null ? new JsonObject() : t.xpRule("capture");
        JsonObject base = rule.has("rarity_xp") ? rule.getAsJsonObject("rarity_xp") : new JsonObject();
        int[] defaults = {20, 40, 80, 150, 400};
        return intOr(base, r.name(), defaults[r.ordinal()]);
    }

    public static CaptureRarity rarity(int catchRate, boolean legendary) {
        PassConfig.Track t = PassConfig.get(PassConfig.COBBLEMON);
        return rarity(catchRate, legendary, t == null ? new JsonObject() : t.xpRule("capture"));
    }

    public static void onCapture(ServerPlayer player, int catchRate, boolean legendary, int level, boolean shiny) {
        int xp = captureXp(catchRate, legendary, level, shiny);
        if (xp > 0) PassData.addXp(player, PassConfig.COBBLEMON, xp);
    }

    /** XP bonus d'une quête de chasse, selon sa rareté (COMMON, UNCOMMON, RARE, LEGENDARY). */
    public static void onChaseObjective(ServerPlayer player, String chaseRarity) {
        PassConfig.Track t = PassConfig.get(PassConfig.COBBLEMON);
        if (t == null) return;
        JsonObject rule = t.xpRule("chase_objective");
        int xp = intOr(rule, chaseRarity, switch (chaseRarity) {
            case "UNCOMMON"  -> 250;
            case "RARE"      -> 500;
            case "LEGENDARY" -> 1000;
            default          -> 120;
        });
        if (xp > 0) PassData.addXp(player, PassConfig.COBBLEMON, xp);
    }

    // ── Voie Create ───────────────────────────────────────────────────

    /**
     * XP pour un item fabriqué, d'après craft_item.items :
     * id exact ("create:cogwheel"), sinon joker de mod ("minecraft:*"), sinon "default".
     * En machine, la valeur est multipliée par machine_multiplier.
     */
    public static double craftXp(Item item, int count, boolean byMachine) {
        PassConfig.Track t = PassConfig.get(PassConfig.CREATE);
        if (t == null || count <= 0) return 0;
        JsonObject rule = t.xpRule("craft_item");
        JsonObject items = rule.has("items") ? rule.getAsJsonObject("items") : new JsonObject();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        double per;
        if (items.has(id.toString()))                    per = items.get(id.toString()).getAsDouble();
        else if (items.has(id.getNamespace() + ":*"))    per = items.get(id.getNamespace() + ":*").getAsDouble();
        else                                             per = doubleOr(rule, "default", 0);
        if (byMachine) per *= doubleOr(rule, "machine_multiplier", 0.5);
        return per * count;
    }

    public static void onItemsCrafted(ServerPlayer player, Item item, int count) {
        addCreateXp(player, craftXp(item, count, false));
    }

    /** Ajoute de l'XP décimale à la voie Create ; le reste est conservé entre deux appels. */
    public static void addCreateXp(ServerPlayer player, double amount) {
        if (amount <= 0) return;
        String key = "creatmon_pass_carry_create";
        double total = player.getPersistentData().getDouble(key) + amount;
        int xp = (int) Math.floor(total);
        player.getPersistentData().putDouble(key, total - xp);
        if (xp > 0) PassData.addXp(player, PassConfig.CREATE, xp);
    }

    /** XP bonus d'une quête /atelier, selon sa difficulté (SIMPLE, MODERE, COMPLEXE, EXPERT). */
    public static void onAtelierObjective(ServerPlayer player, String difficulty) {
        PassConfig.Track t = PassConfig.get(PassConfig.CREATE);
        if (t == null) return;
        int xp = intOr(t.xpRule("atelier_objective"), difficulty, switch (difficulty) {
            case "MODERE"   -> 300;
            case "COMPLEXE" -> 600;
            case "EXPERT"   -> 1200;
            default         -> 120;
        });
        if (xp > 0) PassData.addXp(player, PassConfig.CREATE, xp);
    }

    private static int intOr(JsonObject o, String k, int def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : def;
    }
    private static double doubleOr(JsonObject o, String k, double def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : def;
    }
}
