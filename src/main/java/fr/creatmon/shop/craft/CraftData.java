package fr.creatmon.shop.craft;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class CraftData {

    private static final Logger LOGGER      = LoggerFactory.getLogger("creatmon_craft");
    public  static final long   DURATION_MS = 24L * 60 * 60 * 1000;
    private static final String FILE        = "creatmon_craft.json";
    private static final Gson   GSON        = new GsonBuilder().setPrettyPrinting().create();

    // ── Pools par difficulté ──────────────────────────────────────────

    // Items vérifiés dans Create 6.0.10 (create-1.21.1-6.0.10.jar)
    // create:zinc_sheet n'existe plus dans Create 6 → remplacé par zinc_ingot

    private static final String[] SIMPLE_POOL = {
        "create:andesite_alloy",
        "create:copper_sheet",
        "create:iron_sheet",
        "create:golden_sheet",
        "create:zinc_ingot",
        "create:shaft",
        "create:cogwheel",
        "create:andesite_funnel",
        "create:andesite_tunnel",
        "create:rose_quartz",
    };

    private static final String[] MODERE_POOL = {
        "create:brass_ingot",
        "create:brass_sheet",
        "create:sturdy_sheet",
        "create:large_cogwheel",
        "create:mechanical_bearing",
        "create:turntable",
        "create:mechanical_piston",
        "create:windmill_bearing",
        "create:brass_funnel",
        "create:brass_tunnel",
        "create:spout",
        "create:deployer",
        "create:large_water_wheel",
    };

    private static final String[] COMPLEXE_POOL = {
        "create:flywheel",
        "create:crushing_wheel",
        "create:mechanical_pump",
        "create:mechanical_drill",
        "create:mechanical_press",
        "create:mechanical_mixer",
        "create:encased_fan",
        "create:item_vault",
        "create:fluid_tank",
        "create:steam_engine",
        "create:mechanical_saw",
        "create:mechanical_arm",
    };

    private static final String[] EXPERT_POOL = {
        "create:precision_mechanism",
        "create:sequenced_gearshift",
        "create:rotation_speed_controller",
        "create:mechanical_crafter",
        "create:smart_fluid_pipe",
        "create:chromatic_compound",
        "create:refined_radiance",
    };

    private static final Random RNG = new Random();

    // ── State ─────────────────────────────────────────────────────────

    public static List<CraftTarget>          currentTargets = new ArrayList<>();
    public static long                        nextResetAt    = 0;
    public static Map<UUID, Set<String>>      completions    = new HashMap<>();

    // ── Generation ────────────────────────────────────────────────────

    public static void generateNew() {
        currentTargets.clear();
        completions.clear();
        nextResetAt = System.currentTimeMillis() + DURATION_MS;

        Map<CraftTarget.Difficulty, List<String>> pools = new EnumMap<>(CraftTarget.Difficulty.class);
        pools.put(CraftTarget.Difficulty.SIMPLE,   shuffled(SIMPLE_POOL));
        pools.put(CraftTarget.Difficulty.MODERE,   shuffled(MODERE_POOL));
        pools.put(CraftTarget.Difficulty.COMPLEXE, shuffled(COMPLEXE_POOL));
        pools.put(CraftTarget.Difficulty.EXPERT,   shuffled(EXPERT_POOL));
        Map<CraftTarget.Difficulty, Integer> cursors = new EnumMap<>(CraftTarget.Difficulty.class);

        int generated = 0, attempts = 0;
        while (generated < 6 && attempts < 200) {
            attempts++;
            CraftTarget.Difficulty diff = randomDifficulty();
            List<String> pool = pools.get(diff);
            int cursor = cursors.getOrDefault(diff, 0);
            if (cursor >= pool.size()) continue;
            String itemId = pool.get(cursor);
            cursors.put(diff, cursor + 1);
            if (!itemExistsInRegistry(itemId)) {
                LOGGER.warn("[Atelier] Item absent du registry, ignoré: {}", itemId);
                continue;
            }
            currentTargets.add(new CraftTarget(itemId, diff.defaultQty, diff));
            generated++;
        }

        save();
        LOGGER.info("[Atelier] Nouveaux objectifs générés: {}", currentTargets.stream()
            .map(t -> t.itemId() + "×" + t.quantity() + "(" + t.difficulty().label + ")")
            .collect(Collectors.joining(", ")));
    }

    private static boolean itemExistsInRegistry(String itemId) {
        var rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return false;
        var key = ResourceKey.create(Registries.ITEM, rl);
        return BuiltInRegistries.ITEM.getHolder(key).isPresent();
    }

    private static List<String> shuffled(String[] arr) {
        List<String> list = new ArrayList<>(Arrays.asList(arr));
        Collections.shuffle(list, RNG);
        return list;
    }

    /** Poids : SIMPLE 40%, MODERE 30%, COMPLEXE 20%, EXPERT 10% */
    private static CraftTarget.Difficulty randomDifficulty() {
        int roll = RNG.nextInt(100);
        if (roll < 40) return CraftTarget.Difficulty.SIMPLE;
        if (roll < 70) return CraftTarget.Difficulty.MODERE;
        if (roll < 90) return CraftTarget.Difficulty.COMPLEXE;
        return CraftTarget.Difficulty.EXPERT;
    }

    // ── Completion ────────────────────────────────────────────────────

    /** Returns reward if newly completed, 0 otherwise. */
    public static int tryComplete(UUID playerUUID, String itemId) {
        for (CraftTarget target : currentTargets) {
            if (target.itemId().equals(itemId)) {
                Set<String> done = completions.computeIfAbsent(playerUUID, k -> new HashSet<>());
                if (done.add(target.itemId())) {
                    save();
                    return target.difficulty().reward;
                }
                return 0;
            }
        }
        return 0;
    }

    public static Set<String> getCompletions(UUID uuid) {
        return completions.getOrDefault(uuid, Collections.emptySet());
    }

    public static boolean isExpired() {
        return System.currentTimeMillis() >= nextResetAt;
    }

    public static long timeRemainingMs() {
        return Math.max(0, nextResetAt - System.currentTimeMillis());
    }

    // ── Persistence ───────────────────────────────────────────────────

    public static void load() {
        Path p = FMLPaths.CONFIGDIR.get().resolve(FILE);
        if (!Files.exists(p)) { generateNew(); return; }
        try (Reader r = Files.newBufferedReader(p)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            nextResetAt = root.get("nextResetAt").getAsLong();
            currentTargets.clear();
            for (JsonElement e : root.getAsJsonArray("targets")) {
                JsonObject o = e.getAsJsonObject();
                currentTargets.add(new CraftTarget(
                    o.get("itemId").getAsString(),
                    o.get("quantity").getAsInt(),
                    CraftTarget.Difficulty.valueOf(o.get("difficulty").getAsString())));
            }
            completions.clear();
            if (root.has("completions")) {
                for (var entry : root.getAsJsonObject("completions").entrySet()) {
                    Set<String> set = new HashSet<>();
                    for (JsonElement e : entry.getValue().getAsJsonArray()) set.add(e.getAsString());
                    completions.put(UUID.fromString(entry.getKey()), set);
                }
            }
            if (isExpired()) generateNew();
        } catch (Exception ex) {
            LOGGER.error("[Atelier] Erreur chargement: {}", ex.getMessage());
            generateNew();
        }
    }

    public static void save() {
        Path p = FMLPaths.CONFIGDIR.get().resolve(FILE);
        try {
            Files.createDirectories(p.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("nextResetAt", nextResetAt);
            JsonArray targets = new JsonArray();
            for (CraftTarget t : currentTargets) {
                JsonObject o = new JsonObject();
                o.addProperty("itemId",     t.itemId());
                o.addProperty("quantity",   t.quantity());
                o.addProperty("difficulty", t.difficulty().name());
                targets.add(o);
            }
            root.add("targets", targets);
            JsonObject comps = new JsonObject();
            completions.forEach((uuid, set) -> {
                JsonArray arr = new JsonArray();
                set.forEach(arr::add);
                comps.add(uuid.toString(), arr);
            });
            root.add("completions", comps);
            try (Writer w = Files.newBufferedWriter(p)) { GSON.toJson(root, w); }
        } catch (Exception ex) {
            LOGGER.error("[Atelier] Erreur sauvegarde: {}", ex.getMessage());
        }
    }
}
