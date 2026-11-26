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

public class CreatePool {

    private static final Logger LOGGER = LoggerFactory.getLogger("creatmon_lootbox");
    private static final String CONFIG_FILE = "creatmon_create_pool.json";

    public static List<ItemPool.PoolEntry> POOL = new ArrayList<>();

    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        if (!Files.exists(path)) copyDefault(path);
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<ItemPool.PoolEntry> entries = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray("entries")) {
                JsonObject o = el.getAsJsonObject();
                ItemPool.Rarity rarity = ItemPool.Rarity.valueOf(o.get("rarity").getAsString());
                entries.add(new ItemPool.PoolEntry(
                    o.get("item").getAsString(),
                    o.has("quantity") ? o.get("quantity").getAsInt() : 1,
                    rarity,
                    o.get("weight").getAsInt()
                ));
            }
            POOL = Collections.unmodifiableList(entries);
            LOGGER.info("[CreatePool] Pool chargé : {} entrées", POOL.size());
        } catch (Exception e) {
            LOGGER.error("[CreatePool] Erreur lecture pool : {}", e.getMessage());
        }
    }

    private static void copyDefault(Path dest) {
        try {
            Files.createDirectories(dest.getParent());
            try (InputStream is = CreatePool.class.getResourceAsStream("/data/creatmon_gui/default_create_pool.json")) {
                if (is != null) {
                    Files.copy(is, dest);
                    LOGGER.info("[CreatePool] Pool par défaut créé : {}", dest);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[CreatePool] Impossible de créer le pool par défaut : {}", e.getMessage());
        }
    }

    private static final Random RNG = new Random();

    public static ItemPool.PoolEntry roll() {
        if (POOL.isEmpty()) return new ItemPool.PoolEntry("minecraft:iron_ingot", 1, ItemPool.Rarity.COMMUN, 100);
        int total = POOL.stream().mapToInt(ItemPool.PoolEntry::weight).sum();
        int r = RNG.nextInt(total);
        int acc = 0;
        for (ItemPool.PoolEntry e : POOL) {
            acc += e.weight();
            if (r < acc) return e;
        }
        return POOL.get(0);
    }

    public static List<ItemPool.PoolEntry> getPool() { return POOL; }

    public static List<String> decoys(String excludedId, int count) {
        List<String> ids = POOL.stream().map(ItemPool.PoolEntry::itemId).collect(Collectors.toCollection(ArrayList::new));
        ids.remove(excludedId);
        Collections.shuffle(ids, RNG);
        return ids.subList(0, Math.min(count, ids.size()));
    }

    public static Optional<ItemPool.PoolEntry> findEntry(String itemId) {
        return POOL.stream().filter(e -> e.itemId().equals(itemId)).findFirst();
    }
}
