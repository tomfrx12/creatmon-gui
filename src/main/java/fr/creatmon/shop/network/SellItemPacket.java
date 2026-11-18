package fr.creatmon.shop.network;

import fr.creatmon.shop.ShopData;
import fr.creatmon.shop.ShopMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SellItemPacket(String itemId) implements CustomPacketPayload {

    public static final Type<SellItemPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_shop", "sell_item"));

    public static final StreamCodec<FriendlyByteBuf, SellItemPacket> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SellItemPacket::itemId, SellItemPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SellItemPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            ShopData.ShopItem shopItem = ShopData.findById(packet.itemId());
            if (shopItem == null || !shopItem.sellable()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cCet article n'est pas revendable."));
                return;
            }

            // Find item in player inventory
            var loc  = ResourceLocation.tryParse(shopItem.itemId());
            var item = loc != null ? BuiltInRegistries.ITEM.get(loc) : null;
            if (item == null) return;

            var inventory = player.getInventory();
            int slot = inventory.findSlotMatchingItem(new ItemStack(item));
            if (slot == -1) {
                player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1.0f);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cVous n'avez pas §f" + shopItem.name() + "§c dans votre inventaire."));
                return;
            }

            // Remove 1 item
            inventory.getItem(slot).shrink(1);

            // Add sell price to balance
            int balance    = player.getPersistentData().getInt("cinet_balance");
            int newBalance = balance + shopItem.sell();
            player.getPersistentData().putInt("cinet_balance", newBalance);

            if (player.containerMenu instanceof ShopMenu shopMenu) {
                shopMenu.updateBalance(newBalance);
                shopMenu.broadcastChanges();
            }

            player.playNotifySound(SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 0.6f, 1.0f);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§aVente : §f" + shopItem.name() + " §7(§e+" + shopItem.sell() + " €§7) → solde : §e" + newBalance + " €"
            ));
        });
    }
}
