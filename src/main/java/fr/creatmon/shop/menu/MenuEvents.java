package fr.creatmon.shop.menu;

import com.google.gson.JsonObject;
import fr.creatmon.shop.CreatmonShop;
import fr.creatmon.shop.network.MenuSyncPacket;
import fr.creatmon.shop.pass.PassConfig;
import fr.creatmon.shop.pass.PassEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CreatmonShop.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class MenuEvents {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MenuConfig.load();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("menu")
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                open(player);
                return 1;
            })
            .then(Commands.literal("reload")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    String err = MenuConfig.load();
                    if (err != null) {
                        ctx.getSource().sendFailure(Component.literal(
                            "[Menu] creatmon_menu.json invalide, ancienne config conservée : " + err));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[Menu] Configuration rechargée."), true);
                    return 1;
                })));
    }

    public static void open(ServerPlayer player) {
        int playTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        PacketDistributor.sendToPlayer(player, new MenuSyncPacket(
            MenuConfig.grade,
            0, // prestige : système à venir
            playTicks / 20L,
            new ArrayList<>(MenuConfig.commands.values()),
            PassEvents.trackStates(player),
            howToXp()));
    }

    // ── « Comment XP ? » : généré depuis creatmon_pass.json ───────────

    private static List<String> howToXp() {
        List<String> l = new ArrayList<>();
        PassConfig.Track cob = PassConfig.get(PassConfig.COBBLEMON);
        if (cob != null) {
            JsonObject cap = cob.xpRule("capture");
            JsonObject rx  = obj(cap, "rarity_xp");
            l.add("#POKÉMON");
            l.add("Capturer un Pokémon : XP selon sa rareté + niveau × " + num(cap, "per_level", 2));
            l.add("  Commun " + num(rx, "COMMON", 20) + " · Peu commun " + num(rx, "UNCOMMON", 40)
                + " · Rare " + num(rx, "RARE", 80) + " · Très rare " + num(rx, "VERY_RARE", 150)
                + " · Légendaire " + num(rx, "LEGENDARY", 400));
            l.add("  Shiny : XP × " + num(cap, "shiny_multiplier", 3));
            JsonObject ch = cob.xpRule("chase_objective");
            l.add("Quête /chasse : XP de capture + bonus de quête");
            l.add("  Commune " + num(ch, "COMMON", 120) + " · Peu commune " + num(ch, "UNCOMMON", 250)
                + " · Rare " + num(ch, "RARE", 500) + " · Ultra-rare " + num(ch, "LEGENDARY", 1000));
        }
        PassConfig.Track cre = PassConfig.get(PassConfig.CREATE);
        if (cre != null) {
            JsonObject ci = cre.xpRule("craft_item");
            JsonObject at = cre.xpRule("atelier_objective");
            l.add("#CREATE");
            l.add("Fabriquer un item (table de craft) : XP selon l'item");
            l.add("  ex. alliage d'andésite, roue dentée : peu · mécanisme de précision : plus");
            l.add("Vos machines Create (posées par vous) : XP de l'item × " + num(ci, "machine_multiplier", 0.5));
            l.add("  même quand vous êtes loin ou hors ligne");
            l.add("Quête /atelier : Simple " + num(at, "SIMPLE", 120) + " · Modéré " + num(at, "MODERE", 300)
                + " · Complexe " + num(at, "COMPLEXE", 600) + " · Expert " + num(at, "EXPERT", 1200));
        }
        return l;
    }

    private static JsonObject obj(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    private static String num(JsonObject o, String k, double def) {
        double v = o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : def;
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
