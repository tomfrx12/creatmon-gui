package fr.creatmon.shop.craft;

import fr.creatmon.shop.CreatmonShop;
import fr.creatmon.shop.network.CraftSyncPacket;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@EventBusSubscriber(modid = CreatmonShop.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class CraftEvents {

    private static final Logger LOGGER    = LoggerFactory.getLogger("creatmon_craft");
    private static       int    tickCount = 0;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        CraftData.load();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("atelier")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                    sendSync(player);
                    return 1;
                })
                .then(Commands.literal("reload")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        CraftData.generateNew();
                        MinecraftServer server = ctx.getSource().getServer();
                        server.getPlayerList().getPlayers().forEach(p -> {
                            p.sendSystemMessage(Component.literal(
                                "§b[Atelier Create] §eObjectifs rechargés. Tapez §a/atelier §epour les voir."));
                            sendSync(p);
                        });
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("§a[Atelier] Tableau rechargé."), true);
                        return 1;
                    })));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCount < 20) return;
        tickCount = 0;

        if (CraftData.isExpired()) {
            CraftData.generateNew();
            event.getServer().getPlayerList().getPlayers().forEach(p ->
                p.sendSystemMessage(Component.literal(
                    "§b[Atelier Create] §eNouveaux objectifs disponibles! Tapez §a/atelier §epour les voir.")));
        }

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            checkInventory(player);
        }
    }

    private static void checkInventory(ServerPlayer player) {
        Set<String> done = CraftData.getCompletions(player.getUUID());
        boolean anyNew = false;
        for (CraftTarget target : CraftData.currentTargets) {
            if (done.contains(target.itemId())) continue;
            int count = countItem(player, target.itemId());
            if (count >= target.quantity()) {
                int reward = CraftData.tryComplete(player.getUUID(), target.itemId());
                if (reward > 0) {
                    int bal = player.getPersistentData().getInt("cinet_balance");
                    player.getPersistentData().putInt("cinet_balance", bal + reward);
                    String shortId = target.itemId().contains(":") ? target.itemId().split(":")[1] : target.itemId();
                    player.sendSystemMessage(Component.literal(
                        "§b[Atelier Create] §fObjectif rempli: §e" + shortId +
                        " §7→ §a+" + reward + " €"));
                    fr.creatmon.shop.network.BalanceUtils.sendSync(player);
                    fr.creatmon.shop.pass.PassXp.onAtelierObjective(player, target.difficulty().name());
                    anyNew = true;
                }
            }
        }
        if (anyNew) sendSync(player);
    }

    private static int countItem(ServerPlayer player, String itemId) {
        var rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return 0;
        var key = ResourceKey.create(Registries.ITEM, rl);
        var item = BuiltInRegistries.ITEM.get(key);
        if (item == null || item == Items.AIR) return 0;
        int count = 0;
        for (var stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == item) count += stack.getCount();
        }
        for (var stack : player.getInventory().offhand) {
            if (!stack.isEmpty() && stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    public static void sendSync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new CraftSyncPacket(
            CraftData.currentTargets,
            CraftData.getCompletions(player.getUUID()),
            CraftData.timeRemainingMs()));
    }
}
