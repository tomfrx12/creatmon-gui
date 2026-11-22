package fr.creatmon.shop.lootbox;

import java.util.*;
import java.util.stream.Collectors;

public class PokemonPool {

    // ── Pools ────────────────────────────────────────────────────────
    private static final String[] COMMON = {
        // Gen 1
        "pidgey","rattata","ekans","spearow","nidoranm","nidoranf","diglett","mankey",
        "paras","venonat","shellder","krabby","horsea","goldeen","geodude","tentacool",
        "magikarp","caterpie","weedle","zubat",
        // Gen 2
        "sentret","hoothoot","spinarak","marill","hoppip","wooper","chinchou","natu",
        "sunkern","snubbull","remoraid","phanpy","corsola",
        // Gen 3
        "poochyena","zigzagoon","wurmple","taillow","wingull","makuhita","electrike",
        "skitty","whismur","corphish","barboach","spoink","shuppet",
        // Gen 4
        "starly","bidoof","buizel","shellos","bonsly","burmy","shinx","kricketot",
        // Gen 5
        "patrat","purrloin","pidove","tympole","venipede","lillipup",
        // Gen 6
        "fletchling","bunnelby","scatterbug","pansage","pansear","panpour",
        // Gen 7 & 8
        "blipbug","nickit","rookidee","gossifleur","rolycoly","chewtle","yamper","skwovet",
        // Gen 9
        "lechonk","smoliv","nymble","fidough","greavard",
    };
    private static final String[] UNCOMMON = {
        // Icônes emblématiques
        "pikachu","eevee","clefairy","vulpix","meowth","growlithe","abra","gastly",
        "murkrow","misdreavus","sneasel","teddiursa","houndour","ralts","heracross",
        "togepi","rotom","riolu","zorua","litwick","golett","absol","mimikyu",
        "sandile","tropius","snorunt","gligar","pawmi","applin","sinistea",
        // Starters (1er stade)
        "bulbasaur","charmander","squirtle",
        "chikorita","cyndaquil","totodile",
        "treecko","torchic","mudkip",
        "turtwig","chimchar","piplup",
        "snivy","tepig","oshawott",
        "chespin","fennekin","froakie",
        "rowlet","litten","popplio",
        "grookey","scorbunny","sobble",
        "sprigatito","fuecoco","quaxly",
        // Pré-évolutions bébé
        "happiny","elekid","magby","mimejr","chingling","budew",
    };
    private static final String[] RARE = {
        // Pseudo-légendaires (1er stade)
        "dratini","beldum","bagon","larvitar","gible","deino","axew","goomy",
        "jangmoo","dreepy","frigibax",
        // Starters finaux Gen 1-5
        "charizard","blastoise","venusaur",
        "typhlosion","feraligatr","meganium",
        "blaziken","swampert","sceptile",
        "infernape","empoleon","torterra",
        "serperior","samurott","emboar",
        // Starters finaux Gen 6-9
        "delphox","greninja","chesnaught",
        "incineroar","primarina","decidueye",
        "cinderace","inteleon","rillaboom",
        "skeledirge","quaquaval","meowscarada",
        // Spéciaux
        "lapras","snorlax","feebas","chansey","scyther","pinsir",
        "electabuzz","magmar","spiritomb","lucario",
        "milotic","dragonair","zoroark","sylveon","umbreon","glaceon","leafeon",
    };
    private static final String[] LEGENDARY = {
        // Pseudo-légendaires (formes finales)
        "dragonite","tyranitar","salamence","metagross","garchomp",
        "hydreigon","goodra","kommoo","dragapult","flygon",
        // Gen 1
        "mewtwo","mew","articuno","zapdos","moltres",
        // Gen 2
        "lugia","hooh","celebi","raikou","entei","suicune",
        // Gen 3
        "regirock","regice","registeel","latias","latios",
        "groudon","kyogre","rayquaza","jirachi","deoxys",
        // Gen 4
        "dialga","palkia","giratina","regigigas","heatran",
        "cresselia","darkrai","shaymin","arceus","phione","manaphy",
        // Gen 5
        "cobalion","terrakion","virizion","tornadus","thundurus","landorus",
        "reshiram","zekrom","kyurem","keldeo","meloetta","genesect","victini",
        // Gen 6
        "xerneas","yveltal","zygarde","diancie","hoopa","volcanion",
        // Gen 7 — Tapu
        "tapukoko","tapulele","tapubulu","tapufini",
        // Gen 7 — Legendaires
        "solgaleo","lunala","necrozma","magearna","marshadow","zeraora",
        // Ultra-Bêtes
        "nihilego","buzzwole","pheromosa","xurkitree","celesteela","kartana",
        "guzzlord","naganadel","poipole","stakataka","blacephalon",
        // Gen 8
        "zacian","zamazenta","eternatus","zarude","regieleki","regidrago",
        "glastrier","spectrier","calyrex","enamorus","melmetal",
        // Gen 9 — Paradoxes Passé
        "greattusk","screamtail","brutebonnet","fluttermane","slitherwing",
        "sandyshocks","roaringmoon","koraidon",
        // Gen 9 — Paradoxes Futur
        "irontreads","ironbundle","ironhands","ironjugulis","ironmoth",
        "ironthorns","ironvaliant","walkingwake","ragingbolt","ironcrown",
        "ironleaves","gougingfire","miraidon",
        // Gen 9 — Masque Turquoise / Disque Indigo
        "ogerpon","okidogi","munkidori","fezandipiti","pecharunt","terapagos",
    };

    // Poids par Pokémon selon rareté
    private static final int W_COMMON    = 100;
    private static final int W_UNCOMMON  = 25;
    private static final int W_RARE      = 5;
    private static final int W_LEGENDARY = 1;

    public enum Rarity {
        COMMUN      ("COMMUN",       0xFFFFFFFF),
        PEU_COMMUN  ("PEU COMMUN",   0xFF55DD55),
        RARE        ("RARE",         0xFF5599FF),
        LEGENDAIRE  ("LÉGENDAIRE",   0xFFFFD700);

        public final String label;
        public final int    color;
        Rarity(String label, int color) { this.label = label; this.color = color; }
    }

    public record RollResult(String species, Rarity rarity) {}

    private static final Random RNG = new Random();

    // ── Entrée : calcul du pool complet avec poids ───────────────────

    private record Entry(String species, Rarity rarity, int weight) {}

    private static List<Entry> buildPool() {
        List<Entry> pool = new ArrayList<>();
        for (String s : COMMON)    pool.add(new Entry(s, Rarity.COMMUN,     W_COMMON));
        for (String s : UNCOMMON)  pool.add(new Entry(s, Rarity.PEU_COMMUN, W_UNCOMMON));
        for (String s : RARE)      pool.add(new Entry(s, Rarity.RARE,       W_RARE));
        for (String s : LEGENDARY) pool.add(new Entry(s, Rarity.LEGENDAIRE, W_LEGENDARY));
        return pool;
    }

    public static RollResult roll() {
        List<Entry> pool = buildPool();
        int total = pool.stream().mapToInt(Entry::weight).sum();
        int r = RNG.nextInt(total);
        int acc = 0;
        for (Entry e : pool) {
            acc += e.weight();
            if (r < acc) return new RollResult(e.species(), e.rarity());
        }
        return new RollResult("pikachu", Rarity.COMMUN);
    }

    /** Retourne le pool complet avec probabilité % de chaque espèce. */
    public record PoolEntry(String species, Rarity rarity, float percent) {}

    public static List<PoolEntry> getFullPool() {
        List<Entry> pool = buildPool();
        float total = pool.stream().mapToInt(Entry::weight).sum();
        return pool.stream()
            .map(e -> new PoolEntry(e.species(), e.rarity(), e.weight() / total * 100f))
            .collect(Collectors.toList());
    }

    /** Retourne `count` espèces aléatoires pour l'animation (mélange de toutes raretés). */
    public static List<String> decoys(String excluded, int count) {
        List<String> all = new ArrayList<>();
        all.addAll(Arrays.asList(COMMON));
        all.addAll(Arrays.asList(UNCOMMON));
        all.addAll(Arrays.asList(RARE));
        all.addAll(Arrays.asList(LEGENDARY));
        all.remove(excluded);
        Collections.shuffle(all, RNG);
        return all.subList(0, Math.min(count, all.size()));
    }
}
