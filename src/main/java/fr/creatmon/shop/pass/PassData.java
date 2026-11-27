package fr.creatmon.shop.pass;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

/** Progression par joueur, stockée dans les données persistantes du joueur. */
public class PassData {

    private static String xpKey(String track)      { return "creatmon_pass_xp_" + track; }
    private static String claimedKey(String track) { return "creatmon_pass_claimed_" + track; }

    public record Progress(int level, int xpInLevel, int xpForNext) {}

    public static int getXp(ServerPlayer p, String track) {
        return p.getPersistentData().getInt(xpKey(track));
    }

    public static Set<Integer> getClaimed(ServerPlayer p, String track) {
        Set<Integer> set = new HashSet<>();
        for (int i : p.getPersistentData().getIntArray(claimedKey(track))) set.add(i);
        return set;
    }

    private static void setClaimed(ServerPlayer p, String track, Set<Integer> claimed) {
        p.getPersistentData().putIntArray(claimedKey(track),
            claimed.stream().mapToInt(Integer::intValue).sorted().toArray());
    }

    /** Niveau 1 = départ. Calcule le niveau atteint à partir de l'XP totale. */
    public static Progress progress(PassConfig.Track t, int totalXp) {
        int level = 1, xp = totalXp;
        while (level < t.maxLevel && xp >= t.xpForLevel(level)) {
            xp -= t.xpForLevel(level);
            level++;
        }
        if (level >= t.maxLevel) return new Progress(t.maxLevel, 0, 0);
        return new Progress(level, xp, t.xpForLevel(level));
    }

    public static int level(ServerPlayer p, String track) {
        PassConfig.Track t = PassConfig.get(track);
        return t == null ? 1 : progress(t, getXp(p, track)).level();
    }

    /** Ajoute de l'XP et annonce les montées de niveau. */
    public static void addXp(ServerPlayer p, String track, int amount) {
        PassConfig.Track t = PassConfig.get(track);
        if (t == null || amount <= 0) return;
        int before = level(p, track);
        CompoundTag data = p.getPersistentData();
        data.putInt(xpKey(track), (int) Math.min(Integer.MAX_VALUE, (long) getXp(p, track) + amount));
        int after = level(p, track);
        if (after > before) {
            p.sendSystemMessage(Component.literal(
                "§6[Pass " + t.name + "] §eNiveau §f" + after + " §eatteint ! Tapez §a/pass §epour récupérer vos récompenses."));
        }
        PassEvents.sendSync(p, false, null);
    }

    /** @return nombre de récompenses données. level &lt;= 0 → tout récupérer. */
    public static int claim(ServerPlayer p, String track, int level) {
        PassConfig.Track t = PassConfig.get(track);
        if (t == null) return 0;
        int current = level(p, track);
        Set<Integer> claimed = getClaimed(p, track);
        int given = 0;
        for (PassConfig.Reward r : t.rewards) {
            if (level > 0 && r.level() != level) continue;
            if (r.level() > current || claimed.contains(r.level())) continue;
            ItemStack stack = toStack(r);
            if (stack.isEmpty()) continue;
            if (!p.getInventory().add(stack)) p.drop(stack, false);
            claimed.add(r.level());
            given++;
        }
        if (given > 0) {
            setClaimed(p, track, claimed);
            p.sendSystemMessage(Component.literal(
                "§6[Pass " + t.name + "] §a" + given + " récompense" + (given > 1 ? "s" : "") + " récupérée" + (given > 1 ? "s" : "") + " !"));
        }
        return given;
    }

    public static ItemStack toStack(PassConfig.Reward r) {
        ResourceLocation rl = ResourceLocation.tryParse(r.item());
        if (rl == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.get(rl);
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, Math.max(1, r.count()));
    }

    public static void reset(ServerPlayer p, String track) {
        p.getPersistentData().remove(xpKey(track));
        p.getPersistentData().remove(claimedKey(track));
    }
}
