package fr.creatmon.shop.lootbox;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class ItemPool {

    private static final Logger LOGGER = LoggerFactory.getLogger("creatmon_lootbox");
    private static final String CONFIG_FILE = "creatmon_lootbox_pool.json";

    public enum Rarity {
        COMMUN     ("COMMUN",      0xFFBBBBBB),
        PEU_COMMUN ("PEU COMMUN",  0xFF55DD55),
        RARE       ("RARE",        0xFF5599FF),
        LEGENDAIRE ("LÉGENDAIRE",  0xFFFFD700);

        public final String label;
        public final int    color;
        Rarity(String l, int c) { label = l; color = c; }
    }

    public record PoolEntry(String itemId, int quantity, Rarity rarity, int weight) {}

    private static List<PoolEntry> POOL = new ArrayList<>();

    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        if (!Files.exists(path)) copyDefault(path);
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<PoolEntry> entries = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray("entries")) {
                JsonObject o = el.getAsJsonObject();
                Rarity rarity = Rarity.valueOf(o.get("rarity").getAsString());
                entries.add(new PoolEntry(
                    o.get("item").getAsString(),
                    o.has("quantity") ? o.get("quantity").getAsInt() : 1,
                    rarity,
                    o.get("weight").getAsInt()
                ));
            }
            POOL = Collections.unmodifiableList(entries);
            LOGGER.info("[LootBox] Pool chargé : {} entrées", POOL.size());
        } catch (Exception e) {
            LOGGER.error("[LootBox] Erreur lecture pool : {}", e.getMessage());
        }
    }

    private static void copyDefault(Path dest) {
        try {
            Files.createDirectories(dest.getParent());
            try (InputStream is = ItemPool.class.getResourceAsStream("/data/creatmon_gui/default_lootbox_pool.json")) {
                if (is != null) {
                    Files.copy(is, dest);
                    LOGGER.info("[LootBox] Pool par défaut créé : {}", dest);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[LootBox] Impossible de créer le pool par défaut : {}", e.getMessage());
        }
    }

    private static final Random RNG = new Random();

    public static PoolEntry roll() {
        if (POOL.isEmpty()) return new PoolEntry("minecraft:diamond", 1, Rarity.RARE, 5);
        int total = POOL.stream().mapToInt(PoolEntry::weight).sum();
        int r = RNG.nextInt(total);
        int acc = 0;
        for (PoolEntry e : POOL) {
            acc += e.weight();
            if (r < acc) return e;
        }
        return POOL.get(0);
    }

    public static List<PoolEntry> getPool() { return POOL; }

    public static List<String> decoys(String excludedId, int count) {
        List<String> ids = POOL.stream().map(PoolEntry::itemId).collect(Collectors.toCollection(ArrayList::new));
        ids.remove(excludedId);
        Collections.shuffle(ids, RNG);
        return ids.subList(0, Math.min(count, ids.size()));
    }

    public static Optional<PoolEntry> findEntry(String itemId) {
        return POOL.stream().filter(e -> e.itemId().equals(itemId)).findFirst();
    }
}
