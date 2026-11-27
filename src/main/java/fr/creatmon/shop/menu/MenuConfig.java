package fr.creatmon.shop.menu;

import com.google.gson.*;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config/creatmon_menu.json : commandes des boutons du /menu et grade affiché.
 * Une commande vide → le bouton affiche « Bientôt disponible ».
 */
public class MenuConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("creatmon_menu");
    private static final Gson   GSON   = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Ordre d'affichage des boutons de droite (hors « Comment XP » qui est géré par le GUI). */
    public static final String[] BUTTONS = {"island", "warps", "prestige", "stats"};

    public static String grade = "Admin";
    public static final Map<String, String> commands = new LinkedHashMap<>();

    public static String load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("creatmon_menu.json");
        try {
            if (!Files.exists(path)) {
                JsonObject root = new JsonObject();
                root.addProperty("_aide", "Commande lancée par chaque bouton du /menu, sans le /. Laisser vide = « Bientôt disponible ». Puis /menu reload.");
                root.addProperty("grade", "Admin");
                JsonObject b = new JsonObject();
                for (String k : BUTTONS) b.addProperty(k, "");
                root.add("buttons", b);
                Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            }
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(r, JsonObject.class);
                String g = root.has("grade") ? root.get("grade").getAsString() : "Admin";
                Map<String, String> cmds = new LinkedHashMap<>();
                JsonObject b = root.has("buttons") ? root.getAsJsonObject("buttons") : new JsonObject();
                for (String k : BUTTONS) {
                    String c = b.has(k) ? b.get(k).getAsString().trim() : "";
                    cmds.put(k, c.startsWith("/") ? c.substring(1) : c);
                }
                grade = g;
                commands.clear();
                commands.putAll(cmds);
            }
            return null;
        } catch (Exception e) {
            LOGGER.error("[Menu] Erreur de lecture de {}: {}", path, e.getMessage());
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }
}
