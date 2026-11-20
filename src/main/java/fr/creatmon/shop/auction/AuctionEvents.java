package fr.creatmon.shop.auction;

import fr.creatmon.shop.CreatmonShop;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = CreatmonShop.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class AuctionEvents {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        AuctionData.setRegistries(event.getServer().registryAccess());
        AuctionData.load();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("ah")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                    AuctionOpener.openFor(player);
                    return 1;
                }));
    }

    private static final String   BALANCE_KEY      = "cinet_balance";
    private static final int      STARTING_BALANCE = 2500;
    private static final Set<UUID> pendingSync     = new HashSet<>();

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Initialise le solde si c'est un nouveau joueur
        if (!player.getPersistentData().contains(BALANCE_KEY)) {
            player.getPersistentData().putInt(BALANCE_KEY, STARTING_BALANCE);
        }

        int pending = AuctionData.takePendingBalance(player.getUUID());
        if (pending > 0) {
            int bal = player.getPersistentData().getInt(BALANCE_KEY);
            player.getPersistentData().putInt(BALANCE_KEY, bal + pending);
            player.sendSystemMessage(Component.literal(
                "§a[AH] Revenus perçus pendant votre absence: §e+" + pending + " €"));
        }
        // Délai d'un tick — le client n'est pas encore prêt à recevoir des paquets custom au moment exact du login
        pendingSync.add(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (pendingSync.isEmpty()) return;
        var toRemove = new HashSet<UUID>();
        for (UUID uuid : pendingSync) {
            var player = event.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                fr.creatmon.shop.network.BalanceUtils.sendSync(player);
                toRemove.add(uuid);
            }
        }
        pendingSync.removeAll(toRemove);
    }
}
