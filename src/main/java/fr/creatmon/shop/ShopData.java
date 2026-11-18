package fr.creatmon.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ShopData {

    private static final Logger LOGGER = LoggerFactory.getLogger("creatmon_shop");
    static final String CONFIG_FILE = "creatmon_shop.json";

    public record ShopItem(String id, String itemId, String name, String desc, int buy, int sell, int count) {
        public boolean buyable()  { return buy  > 0; }
        public boolean sellable() { return sell > 0; }
    }

    public record ShopCategory(String id, String label, String iconId, int color, List<ShopItem> items) {}

    public static List<ShopCategory> CATEGORIES = new ArrayList<>();

    public static ShopItem findById(String id) {
        for (ShopCategory cat : CATEGORIES)
            for (ShopItem item : cat.items())
                if (item.id().equals(id)) return item;
        return null;
    }

    public static void load() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);

        if (!Files.exists(configPath)) {
            copyDefaultConfig(configPath);
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            CATEGORIES = parseCategories(root);
            LOGGER.info("[CreatmonShop] Catalogue chargé : {} catégories, {} articles",
                CATEGORIES.size(),
                CATEGORIES.stream().mapToInt(c -> c.items().size()).sum());
        } catch (Exception e) {
            LOGGER.error("[CreatmonShop] Erreur lecture {} : {}", configPath, e.getMessage());
            CATEGORIES = new ArrayList<>();
        }
    }

    private static List<ShopCategory> parseCategories(JsonObject root) {
        List<ShopCategory> cats = new ArrayList<>();
        for (JsonElement catEl : root.getAsJsonArray("categories")) {
            JsonObject cat = catEl.getAsJsonObject();
            List<ShopItem> items = new ArrayList<>();
            for (JsonElement itemEl : cat.getAsJsonArray("items")) {
                JsonObject it = itemEl.getAsJsonObject();
                items.add(new ShopItem(
                    it.get("id").getAsString(),
                    it.get("item").getAsString(),
                    it.get("name").getAsString(),
                    it.has("desc")  ? it.get("desc").getAsString()  : "",
                    it.has("buy")   ? it.get("buy").getAsInt()       : 0,
                    it.has("sell")  ? it.get("sell").getAsInt()      : 0,
                    it.has("count") ? it.get("count").getAsInt()     : 1
                ));
            }
            cats.add(new ShopCategory(
                cat.get("id").getAsString(),
                cat.get("label").getAsString(),
                cat.get("icon").getAsString(),
                parseColor(cat.get("color").getAsString()),
                List.copyOf(items)
            ));
        }
        return List.copyOf(cats);
    }

    private static int parseColor(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (Exception e) {
            return 0xFFAAAAAA;
        }
    }

    private static void copyDefaultConfig(Path dest) {
        try {
            Files.createDirectories(dest.getParent());
            try (InputStream is = ShopData.class.getResourceAsStream("/data/creatmon_shop/default_config.json")) {
                if (is != null) {
                    Files.copy(is, dest);
                    LOGGER.info("[CreatmonShop] Config par défaut créée : {}", dest);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[CreatmonShop] Impossible de créer la config par défaut : {}", e.getMessage());
        }
    }
}
