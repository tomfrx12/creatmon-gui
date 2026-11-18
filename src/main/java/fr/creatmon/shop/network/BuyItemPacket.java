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

public record BuyItemPacket(String itemId) implements CustomPacketPayload {

    public static final Type<BuyItemPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_shop", "buy_item"));

    public static final StreamCodec<FriendlyByteBuf, BuyItemPacket> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, BuyItemPacket::itemId, BuyItemPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BuyItemPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            // Debug: add 1000 C
            if ("__test_add_1000__".equals(packet.itemId())) {
                int cur = player.getPersistentData().getInt("cinet_balance");
                int next = cur + 1000;
                player.getPersistentData().putInt("cinet_balance", next);
                if (player.containerMenu instanceof ShopMenu shopMenu) {
                    shopMenu.updateBalance(next);
                    shopMenu.broadcastChanges();
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a+1 000 C → " + next + " C"));
                return;
            }

            ShopData.ShopItem shopItem = ShopData.findById(packet.itemId());
            if (shopItem == null || !shopItem.buyable()) return;

            int balance = player.getPersistentData().getInt("cinet_balance");
            if (balance < shopItem.buy()) {
                player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1.0f);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cSolde insuffisant."));
                return;
            }

            // Deduct balance
            int newBalance = balance - shopItem.buy();
            player.getPersistentData().putInt("cinet_balance", newBalance);

            // Give item
            var loc  = ResourceLocation.tryParse(shopItem.itemId());
            var item = loc != null ? BuiltInRegistries.ITEM.get(loc) : null;
            if (item != null) {
                ItemStack stack = new ItemStack(item, shopItem.count());
                if (!player.addItem(stack)) player.drop(stack, false);
            }

            // Sync balance back via ShopMenu ContainerData
            if (player.containerMenu instanceof ShopMenu shopMenu) {
                shopMenu.updateBalance(newBalance);
                shopMenu.broadcastChanges();
            }

            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.2f);
            player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("§aAchat : §f" + shopItem.name() +
                    " §7(§e-" + shopItem.buy() + " €§7) → solde : §e" + newBalance + " €")
            );
        });
    }
}
