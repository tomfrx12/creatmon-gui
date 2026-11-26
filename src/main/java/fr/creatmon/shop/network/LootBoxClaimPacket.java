package fr.creatmon.shop.network;

import fr.creatmon.shop.ModItems;
import fr.creatmon.shop.lootbox.CreatePool;
import fr.creatmon.shop.lootbox.ItemPool;
import fr.creatmon.shop.lootbox.PokemonPool;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public record LootBoxClaimPacket(String chestType, int count) implements CustomPacketPayload {

    private static final Logger LOGGER = LoggerFactory.getLogger("creatmon_lootbox");

    public static final Type<LootBoxClaimPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "lootbox_claim"));

    public static final StreamCodec<FriendlyByteBuf, LootBoxClaimPacket> CODEC =
        StreamCodec.of(LootBoxClaimPacket::encode, LootBoxClaimPacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, LootBoxClaimPacket pkt) {
        buf.writeUtf(pkt.chestType());
        buf.writeInt(pkt.count());
    }

    private static LootBoxClaimPacket decode(FriendlyByteBuf buf) {
        return new LootBoxClaimPacket(buf.readUtf(), buf.readInt());
    }

    public static void handle(LootBoxClaimPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            Item keyItem = switch (pkt.chestType()) {
                case "master_ball"    -> ModItems.MASTER_BALL_KEY.get();
                case "create_bronze"  -> ModItems.CREATE_BRONZE_KEY.get();
                case "create_gold"    -> ModItems.CREATE_GOLD_KEY.get();
                default               -> ModItems.POKE_BALL_KEY.get();
            };

            int actualCount = Math.min(Math.max(pkt.count(), 1), 5);

            int remaining = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                var stack = player.getInventory().getItem(i);
                if (stack.is(keyItem)) remaining += stack.getCount();
            }

            boolean isItemBox = "poke_ball".equals(pkt.chestType())
                || "create_bronze".equals(pkt.chestType())
                || "create_gold".equals(pkt.chestType());
            if (isItemBox) {
                // ── Item loot box ────────────────────────────────────
                List<String>  allIds = new ArrayList<>();
                List<Integer> allQty = new ArrayList<>();
                List<Integer> allRar = new ArrayList<>();

                boolean isCreate = "create_bronze".equals(pkt.chestType())
                    || "create_gold".equals(pkt.chestType());
                for (int i = 0; i < actualCount; i++) {
                    int keySlot = -1;
                    for (int s = 0; s < player.getInventory().getContainerSize(); s++) {
                        if (player.getInventory().getItem(s).is(keyItem)) { keySlot = s; break; }
                    }
                    if (keySlot == -1) break;
                    player.getInventory().getItem(keySlot).shrink(1);
                    remaining--;

                    var roll = isCreate ? CreatePool.roll() : ItemPool.roll();
                    allIds.add(roll.itemId());
                    allQty.add(roll.quantity());
                    allRar.add(roll.rarity().ordinal());
                    giveItem(player, roll.itemId(), roll.quantity());
                }

                if (allIds.isEmpty()) return;
                PacketDistributor.sendToPlayer(player,
                    new ItemBoxResultPacket(allIds, allQty, allRar, Math.max(0, remaining)));

            } else {
                // ── Pokémon loot box ─────────────────────────────────
                List<String>  allSpecies  = new ArrayList<>();
                List<Integer> allRarities = new ArrayList<>();

                for (int i = 0; i < actualCount; i++) {
                    int keySlot = -1;
                    for (int s = 0; s < player.getInventory().getContainerSize(); s++) {
                        if (player.getInventory().getItem(s).is(keyItem)) { keySlot = s; break; }
                    }
                    if (keySlot == -1) break;
                    player.getInventory().getItem(keySlot).shrink(1);
                    remaining--;

                    var result = PokemonPool.roll();
                    allSpecies.add(result.species());
                    allRarities.add(result.rarity().ordinal());
                    givePokemon(player, result.species());
                }

                if (allSpecies.isEmpty()) return;

                int bestIdx = 0;
                for (int i = 1; i < allRarities.size(); i++) {
                    if (allRarities.get(i) > allRarities.get(bestIdx)) bestIdx = i;
                }
                String mainSpecies = allSpecies.get(bestIdx);
                int    mainRarity  = allRarities.get(bestIdx);
                List<String> decoys = PokemonPool.decoys(mainSpecies, 40);

                PacketDistributor.sendToPlayer(player,
                    new LootBoxResultPacket(mainSpecies, mainRarity, decoys, Math.max(0, remaining),
                                            allSpecies, allRarities));
            }
        });
    }

    private static void giveItem(ServerPlayer player, String itemId, int qty) {
        try {
            var loc  = net.minecraft.resources.ResourceLocation.parse(itemId);
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(loc);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                LOGGER.warn("[LootBox] Item introuvable: {}", itemId);
                return;
            }
            int remaining = qty;
            while (remaining > 0) {
                int batch = Math.min(remaining, 64);
                player.getInventory().add(new net.minecraft.world.item.ItemStack(item, batch));
                remaining -= batch;
            }
            LOGGER.info("[LootBox] {} a reçu {}×{}", player.getName().getString(), qty, itemId);
        } catch (Throwable t) {
            LOGGER.error("[LootBox] Erreur don item: {}", t.getMessage());
        }
    }

    private static void givePokemon(ServerPlayer player, String speciesName) {
        try {
            var species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE
                .getByName(speciesName);
            if (species == null) {
                LOGGER.warn("[LootBox] Species introuvable: {}", speciesName);
                return;
            }
            var pokemon = new com.cobblemon.mod.common.pokemon.Pokemon();
            pokemon.setSpecies(species);
            pokemon.setLevel(1);
            com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage()
                .getParty(player).add(pokemon);
            LOGGER.info("[LootBox] {} a reçu {}", player.getName().getString(), speciesName);
        } catch (Throwable t) {
            LOGGER.error("[LootBox] Erreur don Pokémon: {}", t.getMessage());
        }
    }
}
