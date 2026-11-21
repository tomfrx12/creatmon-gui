package fr.creatmon.shop;

import fr.creatmon.shop.network.BalanceUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.EntityArgument;

@EventBusSubscriber(modid = CreatmonShop.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ShopCommands {

    private static final String BALANCE_KEY     = "cinet_balance";
    private static final int    STARTING_BALANCE = 2500;

    public static int getBalance(ServerPlayer player) {
        if (!player.getPersistentData().contains(BALANCE_KEY))
            player.getPersistentData().putInt(BALANCE_KEY, STARTING_BALANCE);
        return player.getPersistentData().getInt(BALANCE_KEY);
    }

    public static void setBalance(ServerPlayer player, int amount) {
        player.getPersistentData().putInt(BALANCE_KEY, Math.max(0, amount));
        BalanceUtils.sendSync(player);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("shop")

                // /shop — ouvre la boutique
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                    ShopOpener.openFor(player);
                    return 1;
                })

                // /shop balance
                .then(Commands.literal("balance")
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§6Votre solde : §f" + fmt(getBalance(player)) + " €"), false);
                        return 1;
                    }))

                // /shop give <joueur> <montant>  [OP niveau 2]
                .then(Commands.literal("give")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                setBalance(target, getBalance(target) + amount);
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§a[Shop] +" + fmt(amount) + " € à §f" + target.getName().getString() +
                                    " §a(solde : §f" + fmt(getBalance(target)) + " €§a)."), true);
                                return 1;
                            }))))
        );
    }

    private static String fmt(int n) {
        String s = Integer.toString(n);
        var sb = new StringBuilder();
        int off = s.length() % 3;
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (i - off) % 3 == 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }
}
