package fr.creatmon.shop.pass;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.creatmon.shop.CreatmonShop;
import fr.creatmon.shop.network.PassSyncPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CreatmonShop.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class PassEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger("creatmon_pass");

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        PassConfig.load();
        try {
            Class.forName("fr.creatmon.shop.pass.PassCobblemonHook").getMethod("register").invoke(null);
            LOGGER.info("[Pass] Hook Cobblemon enregistré");
        } catch (Throwable t) {
            LOGGER.warn("[Pass] Hook Cobblemon indisponible: {}", t.getMessage());
        }
    }

    // ── Commandes ─────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        dispatcher.register(build("pass"));
        dispatcher.register(build("rangs"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
            .executes(ctx -> open(ctx, null))
            .then(Commands.literal("cobblemon").executes(ctx -> open(ctx, PassConfig.COBBLEMON)))
            .then(Commands.literal("create").executes(ctx -> open(ctx, PassConfig.CREATE)))
            .then(Commands.literal("reload")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    String err = PassConfig.load();
                    if (err != null) {
                        ctx.getSource().sendFailure(Component.literal(
                            "[Pass] creatmon_pass.json invalide, ancienne config conservée : " + err));
                        return 0;
                    }
                    ctx.getSource().getServer().getPlayerList().getPlayers()
                        .forEach(p -> sendSync(p, false, null));
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[Pass] Configuration rechargée."), true);
                    return 1;
                }))
            .then(Commands.literal("xp")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("track", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(PassConfig.TRACK_IDS, b))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(PassEvents::giveXp)))))
            .then(Commands.literal("reset")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("track", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(PassConfig.TRACK_IDS, b))
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            String track = StringArgumentType.getString(ctx, "track");
                            if (PassConfig.get(track) == null) return badTrack(ctx);
                            PassData.reset(target, track);
                            sendSync(target, false, null);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§a[Pass] Progression " + track + " réinitialisée pour §f" + target.getName().getString()), true);
                            return 1;
                        }))));
    }

    private static int open(CommandContext<CommandSourceStack> ctx, String track) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        sendSync(player, true, track);
        return 1;
    }

    private static int giveXp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        String track = StringArgumentType.getString(ctx, "track");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        if (PassConfig.get(track) == null) return badTrack(ctx);
        PassData.addXp(target, track, amount);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§a[Pass] +" + amount + " XP " + track + " à §f" + target.getName().getString()), true);
        return 1;
    }

    private static int badTrack(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(Component.literal("Voie inconnue (cobblemon | create)"));
        return 0;
    }

    // ── Sources d'XP ──────────────────────────────────────────────────

    /** Craft de n'importe quel item sur une table de craft (ou l'inventaire 2×2). */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var stack = event.getCrafting();
        if (stack.isEmpty()) return;
        PassXp.onItemsCrafted(player, stack.getItem(), stack.getCount());
    }

    /** Ajoute {@code multiplier} × la valeur de la source configurée. */
    public static void award(ServerPlayer player, String track, String source, int multiplier) {
        PassConfig.Track t = PassConfig.get(track);
        if (t == null) return;
        int xp = t.xpSource(source) * Math.max(1, multiplier);
        if (xp > 0) PassData.addXp(player, track, xp);
    }

    // ── Sync ──────────────────────────────────────────────────────────

    public static void sendSync(ServerPlayer player, boolean open, String focusTrack) {
        PacketDistributor.sendToPlayer(player,
            new PassSyncPacket(trackStates(player), open, focusTrack == null ? "" : focusTrack));
    }

    public static List<PassSyncPacket.TrackState> trackStates(ServerPlayer player) {
        List<PassSyncPacket.TrackState> states = new ArrayList<>();
        for (String id : PassConfig.TRACK_IDS) {
            PassConfig.Track t = PassConfig.get(id);
            if (t == null) continue;
            PassData.Progress pr = PassData.progress(t, PassData.getXp(player, id));
            states.add(new PassSyncPacket.TrackState(id, t.name, t.maxLevel,
                pr.level(), pr.xpInLevel(), pr.xpForNext(), PassData.getXp(player, id),
                t.rewards, PassData.getClaimed(player, id)));
        }
        return states;
    }
}
