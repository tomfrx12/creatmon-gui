package fr.creatmon.shop.chase;

import com.google.gson.*;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class ChaseData {

    private static final Logger LOGGER   = LoggerFactory.getLogger("creatmon_chase");
    public  static final long   DURATION_MS = 24L * 60 * 60 * 1000; // 24 heures
    private static final String FILE     = "creatmon_chase.json";
    private static final Gson   GSON     = new GsonBuilder().setPrettyPrinting().create();

    // ── Pools par rareté de spawn (buckets Cobblemon) ────────────────

    /** bucket common — spawn très fréquent */
    private static final String[] COMMON_POOL = {
        "pidgey","rattata","zubat","geodude","tentacool","magikarp","caterpie","weedle",
        "sentret","hoothoot","spinarak","marill","hoppip","wooper","poochyena","zigzagoon",
        "wurmple","taillow","wingull","makuhita","starly","bidoof","buizel","shellos",
        "patrat","purrloin","pidove","tympole","venipede","lillipup","fletchling",
        "bunnelby","scatterbug","pansage","pansear","panpour","blipbug","nickit","rookidee",
    };

    /** bucket uncommon — spawn modéré */
    private static final String[] UNCOMMON_POOL = {
        "pikachu","eevee","clefairy","vulpix","meowth","growlithe","abra","gastly",
        "murkrow","misdreavus","sneasel","teddiursa","houndour","ralts","heracross",
        "togepi","rotom","riolu","zorua","litwick","golett","absol","mimikyu",
        "sandile","tropius","snorunt","gligar","pawmi","applin","sinistea",
    };

    /** bucket rare — spawn difficile (pré-évos pseudos légendaires, Pokémon notoires) */
    private static final String[] RARE_POOL = {
        "dratini","beldum","bagon","larvitar","gible","deino","axew","goomy",
        "jangmoo","dreepy","frigibax","lapras","snorlax","feebas","chansey",
        "scyther","pinsir","electabuzz","magmar","spiritomb","lucario",
    };

    /** bucket ultra_rare — spawn extrêmement rare (pseudo-légendaires finaux, légendaires) */
    private static final String[] LEGENDARY_POOL = {
        "dragonite","tyranitar","salamence","metagross","garchomp",
        "hydreigon","goodra","kommoo","dragapult","flygon",
        "mewtwo","lugia","hooh","raikou","entei","suicune",
        "regirock","regice","registeel","latias","latios",
        "jirachi","darkrai","cresselia","heatran","regigigas",
        "cobalion","terrakion","virizion","tornadus","thundurus","landorus",
        "xerneas","yveltal","zygarde","diancie","volcanion",
    };

    private static final Random RNG = new Random();

    // ── State ─────────────────────────────────────────────────────────

    public static List<ChaseTarget>         currentTargets = new ArrayList<>();
    public static long                       nextResetAt    = 0;
    // per-player: UUID → species names completed this session
    public static Map<UUID, Set<String>>    completions    = new HashMap<>();

    // ── Generation ────────────────────────────────────────────────────

    public static void generateNew() {
        currentTargets.clear();
        completions.clear();
        nextResetAt = System.currentTimeMillis() + DURATION_MS;

        // On tire 6 rarités puis on piocher dans le pool correspondant
        // sans répétition par rareté
        Map<ChaseTarget.Rarity, List<String>> shuffledPools = new EnumMap<>(ChaseTarget.Rarity.class);
        shuffledPools.put(ChaseTarget.Rarity.COMMON,    shuffled(COMMON_POOL));
        shuffledPools.put(ChaseTarget.Rarity.UNCOMMON,  shuffled(UNCOMMON_POOL));
        shuffledPools.put(ChaseTarget.Rarity.RARE,      shuffled(RARE_POOL));
        shuffledPools.put(ChaseTarget.Rarity.LEGENDARY, shuffled(LEGENDARY_POOL));
        Map<ChaseTarget.Rarity, Integer> cursors = new EnumMap<>(ChaseTarget.Rarity.class);

        int generated = 0;
        int attempts  = 0;
        while (generated < 6 && attempts < 100) {
            attempts++;
            ChaseTarget.Rarity rarity = randomRarity();
            List<String> pool   = shuffledPools.get(rarity);
            int cursor          = cursors.getOrDefault(rarity, 0);
            if (cursor >= pool.size()) continue; // pool épuisé pour ce tier
            currentTargets.add(new ChaseTarget(pool.get(cursor), rarity));
            cursors.put(rarity, cursor + 1);
            generated++;
        }

        save();
        LOGGER.info("[Chasse] Nouveaux objectifs générés: {}", currentTargets.stream()
            .map(t -> t.speciesName() + "(" + t.rarity().label + ")")
            .collect(Collectors.joining(", ")));
    }

    private static List<String> shuffled(String[] arr) {
        List<String> list = new ArrayList<>(Arrays.asList(arr));
        Collections.shuffle(list, RNG);
        return list;
    }

    /** Poids : COMMON 40%, UNCOMMON 30%, RARE 20%, LEGENDARY 10% */
    private static ChaseTarget.Rarity randomRarity() {
        int roll = RNG.nextInt(100);
        if (roll < 40) return ChaseTarget.Rarity.COMMON;
        if (roll < 70) return ChaseTarget.Rarity.UNCOMMON;
        if (roll < 90) return ChaseTarget.Rarity.RARE;
        return ChaseTarget.Rarity.LEGENDARY;
    }

    // ── Completion ────────────────────────────────────────────────────

    /** Returns reward if newly completed, 0 otherwise. */
    public static int tryComplete(UUID playerUUID, String speciesCaught) {
        for (ChaseTarget target : currentTargets) {
            if (target.speciesName().equalsIgnoreCase(speciesCaught)) {
                Set<String> done = completions.computeIfAbsent(playerUUID, k -> new HashSet<>());
                if (done.add(target.speciesName())) {
                    save();
                    return target.rarity().reward;
                }
                return 0; // already claimed
            }
        }
        return 0; // not a target
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
                currentTargets.add(new ChaseTarget(
                    o.get("species").getAsString(),
                    ChaseTarget.Rarity.valueOf(o.get("rarity").getAsString())));
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
            LOGGER.error("[Chasse] Erreur chargement: {}", ex.getMessage());
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
            for (ChaseTarget t : currentTargets) {
                JsonObject o = new JsonObject();
                o.addProperty("species", t.speciesName());
                o.addProperty("rarity",  t.rarity().name());
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
            LOGGER.error("[Chasse] Erreur sauvegarde: {}", ex.getMessage());
        }
    }
}
