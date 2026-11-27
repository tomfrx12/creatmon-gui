package fr.creatmon.shop.pass;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import fr.creatmon.shop.network.XpTablePacket;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Chargé par réflexion : ne casse rien si Cobblemon est absent. */
public class PassCobblemonHook {

    public static void register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, event -> {
            if (event.getPlayer() instanceof ServerPlayer player) {
                Pokemon p = event.getPokemon();
                boolean legendary = p.isLegendary() || p.isMythical() || p.isUltraBeast();
                PassXp.onCapture(player, p.getSpecies().getCatchRate(), legendary, p.getLevel(), p.getShiny());
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    /** Toutes les espèces implémentées avec leur rareté et leur XP de base de capture. */
    public static List<XpTablePacket.PokeEntry> speciesEntries() {
        List<XpTablePacket.PokeEntry> out = new ArrayList<>();
        for (Species sp : PokemonSpecies.getImplemented()) {
            var labels = sp.getLabels();
            boolean legendary = labels.contains("legendary") || labels.contains("mythical") || labels.contains("ultra_beast");
            PassXp.CaptureRarity r = PassXp.rarity(sp.getCatchRate(), legendary);
            String key = sp.getTranslatedName().getContents() instanceof TranslatableContents tc ? tc.getKey() : "";
            out.add(new XpTablePacket.PokeEntry(sp.getNationalPokedexNumber(), key, sp.getName(),
                r.ordinal(), PassXp.rarityXp(r)));
        }
        return out;
    }
}
