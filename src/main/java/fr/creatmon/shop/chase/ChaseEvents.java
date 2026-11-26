package fr.creatmon.shop.chase;

import fr.creatmon.shop.CreatmonShop;
import fr.creatmon.shop.network.ChaseSyncPacket;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = CreatmonShop.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ChaseEvents {

    private static final Logger LOGGER    = LoggerFactory.getLogger("creatmon_chase");
    private static       int    tickCount = 0;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ChaseData.load();
        registerCobblemonHook();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("chasse")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                    sendSync(player);
                    return 1;
                })
                .then(Commands.literal("reload")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        ChaseData.generateNew();
                        MinecraftServer server = ctx.getSource().getServer();
                        server.getPlayerList().getPlayers().forEach(p -> {
                            p.sendSystemMessage(Component.literal(
                                "§6[Tableau de chasse] §eObjectifs rechargés par un admin. Tapez §a/chasse §epour les voir."));
                            sendSync(p);
                        });
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("§a[Chasse] Tableau rechargé avec succès."), true);
                        return 1;
                    })));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Check timer expiry every second (20 ticks)
        if (++tickCount < 20) return;
        tickCount = 0;
        if (ChaseData.isExpired()) {
            ChaseData.generateNew();
            MinecraftServer server = event.getServer();
            server.getPlayerList().getPlayers().forEach(p ->
                p.sendSystemMessage(Component.literal(
                    "§6[Tableau de chasse] §eNouveaux objectifs disponibles! Tapez §a/chasse §epour les voir.")));
        }
    }

    // ── Send sync to player ───────────────────────────────────────────

    public static void sendSync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ChaseSyncPacket(
            ChaseData.currentTargets,
            ChaseData.getCompletions(player.getUUID()),
            ChaseData.timeRemainingMs()));
    }

    // ── Cobblemon hook ────────────────────────────────────────────────

    private static void registerCobblemonHook() {
        try {
            Class.forName("fr.creatmon.shop.chase.CobblemonChaseHook")
                 .getMethod("register")
                 .invoke(null);
            LOGGER.info("[Chasse] Hook Cobblemon enregistré (POKEMON_CAPTURED)");
        } catch (Throwable t) {
            LOGGER.warn("[Chasse] Hook Cobblemon indisponible: {}", t.getMessage());
        }
    }

    static void handleCapture(ServerPlayer player, String species) {
        int reward = ChaseData.tryComplete(player.getUUID(), species);
        if (reward > 0) {
            int bal = player.getPersistentData().getInt("cinet_balance");
            player.getPersistentData().putInt("cinet_balance", bal + reward);
            player.sendSystemMessage(Component.literal(
                "§6[Tableau de chasse] §fObjectif complété: §e" +
                capitalize(species) + " §7→ §a+" + reward + " €"));
            fr.creatmon.shop.network.BalanceUtils.sendSync(player);
            sendSync(player);
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
