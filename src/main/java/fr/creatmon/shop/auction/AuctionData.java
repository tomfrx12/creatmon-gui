package fr.creatmon.shop.auction;

import com.google.gson.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class AuctionData {

    private static final Logger LOGGER    = LoggerFactory.getLogger("creatmon_ah");
    private static final String FILE      = "creatmon_ah.json";
    private static final Gson   GSON      = new GsonBuilder().setPrettyPrinting().create();

    public static final long DURATION_MS  = 2L * 24 * 60 * 60 * 1000; // 2 jours
    private static final int  MAX_HISTORY = 20;

    private static HolderLookup.Provider registries;

    // ── Records ───────────────────────────────────────────────────────

    public record Listing(UUID id, UUID sellerUUID, String sellerName,
                          String itemJson, int quantity, long price, long listedAt) {
        public boolean isExpired() { return System.currentTimeMillis() > listedAt + DURATION_MS; }
        public ItemStack toItemStack() { return decodeItem(itemJson); }
    }

    public record InboxEntry(String itemJson, long addedAt) {
        public ItemStack toItemStack() { return decodeItem(itemJson); }
    }

    // ── State ─────────────────────────────────────────────────────────

    public static final List<Listing>              LISTINGS          = new ArrayList<>();
    public static final Map<UUID, List<InboxEntry>> INBOX            = new HashMap<>();
    public static final Map<String, List<Long>>    PRICE_HISTORY     = new HashMap<>();
    public static final Map<UUID, Integer>         PENDING_BALANCES  = new HashMap<>();

    public static void setRegistries(HolderLookup.Provider r) { registries = r; }

    // ── Listings ──────────────────────────────────────────────────────

    public static void addListing(ServerPlayer seller, ItemStack item, int quantity, long price) {
        ItemStack copy = item.copy();
        copy.setCount(quantity);
        LISTINGS.add(new Listing(UUID.randomUUID(), seller.getUUID(),
            seller.getName().getString(), encodeItem(copy), quantity, price,
            System.currentTimeMillis()));
        save();
    }

    public static void removeListing(UUID id) {
        LISTINGS.removeIf(l -> l.id().equals(id));
        save();
    }

    public static Optional<Listing> findListing(UUID id) {
        return LISTINGS.stream().filter(l -> l.id().equals(id)).findFirst();
    }

    // ── Inbox ─────────────────────────────────────────────────────────

    public static void addToInbox(UUID playerUUID, ItemStack item) {
        INBOX.computeIfAbsent(playerUUID, k -> new ArrayList<>())
             .add(new InboxEntry(encodeItem(item), System.currentTimeMillis()));
        save();
    }

    public static List<ItemStack> getInboxItems(UUID playerUUID) {
        return INBOX.getOrDefault(playerUUID, List.of()).stream()
            .map(InboxEntry::toItemStack)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    public static void clearInbox(UUID playerUUID) {
        INBOX.remove(playerUUID);
        save();
    }

    public static void setInboxItems(UUID playerUUID, List<ItemStack> remaining) {
        if (remaining.isEmpty()) { INBOX.remove(playerUUID); }
        else {
            List<InboxEntry> entries = remaining.stream()
                .map(it -> new InboxEntry(encodeItem(it), System.currentTimeMillis()))
                .collect(Collectors.toList());
            INBOX.put(playerUUID, entries);
        }
        save();
    }

    // ── Pending balance (vendeur hors ligne) ──────────────────────────

    public static void addPendingBalance(UUID uuid, int amount) {
        PENDING_BALANCES.merge(uuid, amount, Integer::sum);
        save();
    }

    public static int takePendingBalance(UUID uuid) {
        int v = PENDING_BALANCES.getOrDefault(uuid, 0);
        PENDING_BALANCES.remove(uuid);
        if (v > 0) save();
        return v;
    }

    // ── Price history ─────────────────────────────────────────────────

    public static void recordSale(String itemId, long price) {
        List<Long> hist = PRICE_HISTORY.computeIfAbsent(itemId, k -> new ArrayList<>());
        hist.add(price);
        if (hist.size() > MAX_HISTORY) hist.remove(0);
        save();
    }

    public static double getAvgPrice(String itemId) {
        List<Long> h = PRICE_HISTORY.get(itemId);
        if (h == null || h.isEmpty()) return 0;
        return h.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    public static Map<String, Double> getAllAvgPrices() {
        Map<String, Double> map = new HashMap<>();
        PRICE_HISTORY.forEach((id, hist) -> {
            if (!hist.isEmpty())
                map.put(id, hist.stream().mapToLong(Long::longValue).average().orElse(0));
        });
        return map;
    }

    // ── Prune expired listings → inbox vendeur ────────────────────────

    public static void pruneExpired() {
        boolean changed = false;
        var it = LISTINGS.iterator();
        while (it.hasNext()) {
            Listing l = it.next();
            if (l.isExpired()) {
                addToInbox(l.sellerUUID(), l.toItemStack());
                it.remove();
                changed = true;
            }
        }
        if (changed) save();
    }

    // ── Persistence ───────────────────────────────────────────────────

    public static void load() {
        Path p = FMLPaths.CONFIGDIR.get().resolve(FILE);
        if (!Files.exists(p)) { save(); return; }
        try (Reader r = Files.newBufferedReader(p)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();

            LISTINGS.clear();
            if (root.has("listings")) {
                for (JsonElement e : root.getAsJsonArray("listings")) {
                    JsonObject o = e.getAsJsonObject();
                    LISTINGS.add(new Listing(
                        UUID.fromString(o.get("id").getAsString()),
                        UUID.fromString(o.get("sellerUUID").getAsString()),
                        o.get("sellerName").getAsString(),
                        o.get("item").getAsString(),
                        o.get("quantity").getAsInt(),
                        o.get("price").getAsLong(),
                        o.get("listedAt").getAsLong()));
                }
            }

            INBOX.clear();
            if (root.has("inbox")) {
                for (var entry : root.getAsJsonObject("inbox").entrySet()) {
                    UUID uuid = UUID.fromString(entry.getKey());
                    List<InboxEntry> list = new ArrayList<>();
                    for (JsonElement e : entry.getValue().getAsJsonArray()) {
                        JsonObject o = e.getAsJsonObject();
                        list.add(new InboxEntry(o.get("item").getAsString(), o.get("addedAt").getAsLong()));
                    }
                    INBOX.put(uuid, list);
                }
            }

            PRICE_HISTORY.clear();
            if (root.has("priceHistory")) {
                for (var entry : root.getAsJsonObject("priceHistory").entrySet()) {
                    List<Long> prices = new ArrayList<>();
                    for (JsonElement e : entry.getValue().getAsJsonArray()) prices.add(e.getAsLong());
                    PRICE_HISTORY.put(entry.getKey(), prices);
                }
            }

            PENDING_BALANCES.clear();
            if (root.has("pendingBalances")) {
                for (var entry : root.getAsJsonObject("pendingBalances").entrySet()) {
                    PENDING_BALANCES.put(UUID.fromString(entry.getKey()), entry.getValue().getAsInt());
                }
            }

            LOGGER.info("[AH] Chargé: {} annonces", LISTINGS.size());
        } catch (Exception ex) {
            LOGGER.error("[AH] Erreur chargement: {}", ex.getMessage());
        }
    }

    public static void save() {
        Path p = FMLPaths.CONFIGDIR.get().resolve(FILE);
        try {
            Files.createDirectories(p.getParent());
            JsonObject root = new JsonObject();

            JsonArray listings = new JsonArray();
            for (Listing l : LISTINGS) {
                JsonObject o = new JsonObject();
                o.addProperty("id",         l.id().toString());
                o.addProperty("sellerUUID", l.sellerUUID().toString());
                o.addProperty("sellerName", l.sellerName());
                o.addProperty("item",       l.itemJson());
                o.addProperty("quantity",   l.quantity());
                o.addProperty("price",      l.price());
                o.addProperty("listedAt",   l.listedAt());
                listings.add(o);
            }
            root.add("listings", listings);

            JsonObject inbox = new JsonObject();
            INBOX.forEach((uuid, entries) -> {
                JsonArray arr = new JsonArray();
                for (InboxEntry ie : entries) {
                    JsonObject o = new JsonObject();
                    o.addProperty("item",    ie.itemJson());
                    o.addProperty("addedAt", ie.addedAt());
                    arr.add(o);
                }
                inbox.add(uuid.toString(), arr);
            });
            root.add("inbox", inbox);

            JsonObject hist = new JsonObject();
            PRICE_HISTORY.forEach((id, prices) -> {
                JsonArray arr = new JsonArray();
                prices.forEach(arr::add);
                hist.add(id, arr);
            });
            root.add("priceHistory", hist);

            JsonObject pending = new JsonObject();
            PENDING_BALANCES.forEach((uuid, v) -> pending.addProperty(uuid.toString(), v));
            root.add("pendingBalances", pending);

            try (Writer w = Files.newBufferedWriter(p)) { GSON.toJson(root, w); }
        } catch (Exception ex) {
            LOGGER.error("[AH] Erreur sauvegarde: {}", ex.getMessage());
        }
    }

    // ── Codec helpers ─────────────────────────────────────────────────

    public static String encodeItem(ItemStack item) {
        if (registries == null || item.isEmpty()) return "{}";
        try {
            var ops = RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries);
            return GSON.toJson(ItemStack.CODEC.encodeStart(ops, item)
                .getOrThrow(e -> new RuntimeException(e)));
        } catch (Exception e) { return "{}"; }
    }

    public static ItemStack decodeItem(String data) {
        if (registries == null || data == null || data.isBlank() || data.equals("{}"))
            return ItemStack.EMPTY;
        try {
            var ops = RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries);
            return ItemStack.CODEC.parse(ops, JsonParser.parseString(data))
                .getOrThrow(e -> new RuntimeException(e));
        } catch (Exception e) { return ItemStack.EMPTY; }
    }
}
