package fr.creatmon.shop.pass;

import com.google.gson.*;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Configuration des passes de progression — config/creatmon_pass.json.
 * Deux voies indépendantes : "cobblemon" et "create", chacune avec ses
 * niveaux, sa courbe d'XP, ses sources d'XP et ses récompenses.
 */
public class PassConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("creatmon_pass");
    private static final Gson   GSON   = new GsonBuilder().setPrettyPrinting().create();

    public static final String COBBLEMON = "cobblemon";
    public static final String CREATE    = "create";
    public static final List<String> TRACK_IDS = List.of(COBBLEMON, CREATE);

    public record Reward(int level, String item, int count) {}

    public static final class Track {
        public final String id;
        public final String name;
        public final int    maxLevel;
        public final int    xpBase;
        public final int    xpGrowth;
        public final Map<String, JsonElement> xpSources;
        public final List<Reward> rewards;

        Track(String id, String name, int maxLevel, int xpBase, int xpGrowth,
              Map<String, JsonElement> xpSources, List<Reward> rewards) {
            this.id = id; this.name = name; this.maxLevel = maxLevel;
            this.xpBase = xpBase; this.xpGrowth = xpGrowth;
            this.xpSources = xpSources; this.rewards = rewards;
        }

        /** XP nécessaire pour passer du niveau {@code level} au suivant. */
        public int xpForLevel(int level) {
            return Math.max(1, xpBase + xpGrowth * (level - 1));
        }

        public int xpSource(String key) {
            JsonElement e = xpSources.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : 0;
        }

        /** Source d'XP détaillée (objet JSON), ou objet vide. */
        public JsonObject xpRule(String key) {
            JsonElement e = xpSources.get(key);
            return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        }

        public Reward rewardAt(int level) {
            for (Reward r : rewards) if (r.level() == level) return r;
            return null;
        }
    }

    private static final Map<String, Track> tracks = new LinkedHashMap<>();

    public static Track get(String id) { return tracks.get(id); }

    public static Path path() { return FMLPaths.CONFIGDIR.get().resolve("creatmon_pass.json"); }

    /**
     * Charge (ou recharge) le fichier. En cas d'erreur, l'ancienne config reste active.
     * @return null si OK, sinon le message d'erreur.
     */
    public static String load() {
        Path path = path();
        if (!Files.exists(path)) copyDefault(path);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            Map<String, Track> loaded = new LinkedHashMap<>();
            for (String id : TRACK_IDS) {
                JsonObject t = root.has(id) ? root.getAsJsonObject(id) : new JsonObject();
                Map<String, JsonElement> sources = new HashMap<>();
                if (t.has("xp_sources"))
                    for (var e : t.getAsJsonObject("xp_sources").entrySet())
                        sources.put(e.getKey(), e.getValue());
                List<Reward> rewards = new ArrayList<>();
                if (t.has("rewards"))
                    for (JsonElement el : t.getAsJsonArray("rewards")) {
                        JsonObject o = el.getAsJsonObject();
                        rewards.add(new Reward(
                            o.get("level").getAsInt(),
                            o.get("item").getAsString(),
                            o.has("count") ? o.get("count").getAsInt() : 1));
                    }
                rewards.sort(Comparator.comparingInt(Reward::level));
                int maxFromRewards = rewards.isEmpty() ? 1 : rewards.get(rewards.size() - 1).level();
                loaded.put(id, new Track(id,
                    t.has("name") ? t.get("name").getAsString() : id,
                    t.has("max_level") ? t.get("max_level").getAsInt() : maxFromRewards,
                    t.has("xp_base") ? t.get("xp_base").getAsInt() : 200,
                    t.has("xp_growth") ? t.get("xp_growth").getAsInt() : 25,
                    sources, rewards));
            }
            tracks.clear();
            tracks.putAll(loaded);
            LOGGER.info("[Pass] Configuration chargée ({} / {} récompenses)",
                tracks.get(COBBLEMON).rewards.size(), tracks.get(CREATE).rewards.size());
            return null;
        } catch (Exception e) {
            LOGGER.error("[Pass] Erreur de lecture de {}: {}", path, e.getMessage());
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    // ── Défaut : copie de data/creatmon_gui/default_pass.json ─────────

    private static void copyDefault(Path dest) {
        try {
            Files.createDirectories(dest.getParent());
            try (InputStream is = PassConfig.class.getResourceAsStream("/data/creatmon_gui/default_pass.json")) {
                if (is != null) {
                    Files.copy(is, dest);
                    LOGGER.info("[Pass] Configuration par défaut créée : {}", dest);
                }
            }
        } catch (IOException e) {
            LOGGER.error("[Pass] Impossible de créer {}: {}", dest, e.getMessage());
        }
    }
}
