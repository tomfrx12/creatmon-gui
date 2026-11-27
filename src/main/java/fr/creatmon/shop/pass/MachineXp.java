package fr.creatmon.shop.pass;

import fr.creatmon.shop.CreatmonShop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * XP des machines Create : donnée au joueur qui a posé la machine.
 * L XP est cumulée et versée toutes les 5 secondes ;
 * si le propriétaire est hors ligne, l'XP l'attend jusqu'à sa prochaine connexion.
 */
@EventBusSubscriber(modid = CreatmonShop.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class MachineXp {

    private static final Map<UUID, Double> buffer = new HashMap<>();
    private static int tick = 0;

    /** Appelé par les mixins Create quand une machine produit {@code count} × {@code item}. */
    public static void record(Level level, BlockPos pos, Item item, int count) {
        if (level == null || level.isClientSide() || item == null || count <= 0) return;
        double xp = PassXp.craftXp(item, count, true);
        if (xp <= 0) return;
        UUID owner = MachineOwners.find(level, pos);
        if (owner != null) buffer.merge(owner, xp, Double::sum);
    }

    /** Contenu d'un inventaire (à partir de {@code fromSlot}), regroupé par item. */
    public static Map<Item, Integer> snapshot(IItemHandler inv, int fromSlot) {
        Map<Item, Integer> m = new HashMap<>();
        for (int i = fromSlot; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) m.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return m;
    }

    /** Enregistre ce qui est apparu dans l'inventaire depuis {@code before}. */
    public static void recordDiff(Level level, BlockPos pos, Map<Item, Integer> before, IItemHandler inv, int fromSlot) {
        if (before == null) return;
        snapshot(inv, fromSlot).forEach((item, n) -> record(level, pos, item, n - before.getOrDefault(item, 0)));
    }

    // ── Propriétaire : enregistré à la pose, retiré à la casse ────────

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var id = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock());
        if ("create".equals(id.getNamespace()))
            MachineOwners.get(level).set(event.getPos(), player.getUUID());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level)
            MachineOwners.get(level).remove(event.getPos());
    }

    // ── Conversion en XP ──────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tick < 100 || buffer.isEmpty()) return;
        tick = 0;
        MinecraftServer server = event.getServer();
        Pending pending = Pending.get(server);
        for (var e : buffer.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) PassXp.addCreateXp(p, e.getValue());
            else pending.add(e.getKey(), e.getValue());
        }
        buffer.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        double xp = Pending.get(p.server).take(p.getUUID());
        if (xp > 0) PassXp.addCreateXp(p, xp);
    }

    /** XP des machines produite pendant que le propriétaire était hors ligne. */
    static class Pending extends SavedData {
        private final Map<UUID, Double> items = new HashMap<>();

        static Pending get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Pending::new, Pending::load, null), "creatmon_pass_pending");
        }

        void add(UUID id, double n) { items.merge(id, n, Double::sum); setDirty(); }

        double take(UUID id) {
            Double n = items.remove(id);
            if (n != null) setDirty();
            return n == null ? 0 : n;
        }

        static Pending load(CompoundTag tag, HolderLookup.Provider provider) {
            Pending p = new Pending();
            for (String k : tag.getAllKeys()) p.items.put(UUID.fromString(k), tag.getDouble(k));
            return p;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            items.forEach((k, v) -> tag.putDouble(k.toString(), v));
            return tag;
        }
    }
}
