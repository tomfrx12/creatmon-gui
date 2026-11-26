package fr.creatmon.shop.chase;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.minecraft.server.level.ServerPlayer;

public class CobblemonChaseHook {

    public static void register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(
            Priority.NORMAL,
            event -> {
                if (event.getPlayer() instanceof ServerPlayer player) {
                    String species = event.getPokemon().getSpecies().getName().toLowerCase();
                    ChaseEvents.handleCapture(player, species);
                }
                return kotlin.Unit.INSTANCE;
            });
    }
}
