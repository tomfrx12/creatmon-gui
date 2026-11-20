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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SellItemPacket(String itemId, int quantity) implements CustomPacketPayload {

    public static final Type<SellItemPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "sell_item"));

    public static final StreamCodec<FriendlyByteBuf, SellItemPacket> CODEC =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SellItemPacket::itemId,
                ByteBufCodecs.VAR_INT,     SellItemPacket::quantity,
                SellItemPacket::new);

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

            var loc  = ResourceLocation.tryParse(shopItem.itemId());
            var item = loc != null ? BuiltInRegistries.ITEM.get(loc) : null;
            if (item == null) return;

            var inventory = player.getInventory();

            // Count how many the player has across all slots
            int available = 0;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack s = inventory.getItem(i);
                if (!s.isEmpty() && s.getItem() == item) available += s.getCount();
            }

            int actualQty = Math.min(Math.max(1, packet.quantity()), available);
            if (actualQty == 0) {
                player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1.0f);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cVous n'avez pas §f" + shopItem.name() + "§c dans votre inventaire."));
                return;
            }

            // Remove items across slots
            int toRemove = actualQty;
            for (int i = 0; i < inventory.getContainerSize() && toRemove > 0; i++) {
                ItemStack s = inventory.getItem(i);
                if (!s.isEmpty() && s.getItem() == item) {
                    int r = Math.min(s.getCount(), toRemove);
                    s.shrink(r);
                    toRemove -= r;
                }
            }

            int revenue    = shopItem.sell() * actualQty;
            int balance    = player.getPersistentData().getInt("cinet_balance");
            int newBalance = balance + revenue;
            player.getPersistentData().putInt("cinet_balance", newBalance);

            if (player.containerMenu instanceof ShopMenu shopMenu) {
                shopMenu.updateBalance(newBalance);
                shopMenu.broadcastChanges();
            }
            BalanceUtils.sendSync(player);

            player.playNotifySound(SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 0.6f, 1.0f);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§aVente ×" + actualQty + " : §f" + shopItem.name() +
                " §7(§e+" + revenue + " €§7) → solde : §e" + newBalance + " €"
            ));
        });
    }
}
