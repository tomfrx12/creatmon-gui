package fr.creatmon.shop.lootbox;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class LootBoxConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, List<Reward>> rewardMap = new HashMap<>();

    public record Reward(String item, int weight, int count) {}

    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("creatmon_lootbox.json");
        if (!Files.exists(path)) writeDefault(path);
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            rewardMap.clear();
            for (var entry : root.entrySet()) {
                List<Reward> list = new ArrayList<>();
                for (JsonElement el : entry.getValue().getAsJsonArray()) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new Reward(
                        o.get("item").getAsString(),
                        o.get("weight").getAsInt(),
                        o.has("count") ? o.get("count").getAsInt() : 1
                    ));
                }
                rewardMap.put(entry.getKey(), list);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ItemStack pick(String type) {
        List<Reward> pool = rewardMap.getOrDefault(type, List.of());
        if (pool.isEmpty()) return ItemStack.EMPTY;
        int total = pool.stream().mapToInt(Reward::weight).sum();
        int roll = new Random().nextInt(total);
        int acc = 0;
        for (Reward r : pool) {
            acc += r.weight;
            if (roll < acc)
                return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(r.item)), r.count);
        }
        return ItemStack.EMPTY;
    }

    private static void writeDefault(Path path) {
        JsonObject root = new JsonObject();
        addRewards(root, "poke_ball",
            reward("minecraft:iron_ingot",  3, 40),
            reward("minecraft:gold_ingot",  1, 20),
            reward("minecraft:diamond",     1,  5),
            reward("minecraft:emerald",     1,  5),
            reward("minecraft:bread",       4, 30));
        addRewards(root, "master_ball",
            reward("minecraft:diamond",     3, 40),
            reward("minecraft:netherite_ingot", 1, 10),
            reward("minecraft:enchanted_golden_apple", 1, 5),
            reward("minecraft:nether_star", 1,  5),
            reward("minecraft:emerald",     5, 40));
        addRewards(root, "create_bronze",
            reward("minecraft:iron_ingot",  4, 50),
            reward("minecraft:copper_ingot",8, 50),
            reward("minecraft:gold_ingot",  2, 20),
            reward("minecraft:diamond",     1,  5));
        addRewards(root, "create_gold",
            reward("minecraft:gold_ingot",  4, 50),
            reward("minecraft:diamond",     2, 20),
            reward("minecraft:netherite_scrap", 1, 10),
            reward("minecraft:emerald",     3, 20));
        try { Files.writeString(path, GSON.toJson(root)); } catch (IOException ex) { ex.printStackTrace(); }
    }

    private static JsonObject reward(String item, int count, int weight) {
        JsonObject o = new JsonObject();
        o.addProperty("item", item);
        o.addProperty("weight", weight);
        o.addProperty("count", count);
        return o;
    }

    private static void addRewards(JsonObject root, String type, JsonObject... rewards) {
        JsonArray arr = new JsonArray();
        for (JsonObject r : rewards) arr.add(r);
        root.add(type, arr);
    }
}
