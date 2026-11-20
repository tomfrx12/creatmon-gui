package fr.creatmon.shop.network;

import fr.creatmon.shop.auction.AuctionData;
import fr.creatmon.shop.auction.AuctionOpener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AHListPacket(int inventorySlot, int quantity, long price) implements CustomPacketPayload {

    public static final Type<AHListPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "ah_list"));

    public static final StreamCodec<FriendlyByteBuf, AHListPacket> CODEC = StreamCodec.of(
        (buf, pkt) -> { buf.writeInt(pkt.inventorySlot()); buf.writeInt(pkt.quantity()); buf.writeLong(pkt.price()); },
        buf -> new AHListPacket(buf.readInt(), buf.readInt(), buf.readLong()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AHListPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (pkt.price() <= 0 || pkt.quantity() <= 0) return;

            var inv  = player.getInventory();
            int slot = pkt.inventorySlot();
            if (slot < 0 || slot >= inv.getContainerSize()) return;

            ItemStack slotItem = inv.getItem(slot);
            if (slotItem.isEmpty() || slotItem.getCount() < pkt.quantity()) {
                player.sendSystemMessage(Component.literal("§cPas assez d'items dans ce slot."));
                return;
            }

            ItemStack toList = slotItem.copy();
            toList.setCount(pkt.quantity());
            slotItem.shrink(pkt.quantity());

            AuctionData.addListing(player, toList, pkt.quantity(), pkt.price());

            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.0f);
            player.sendSystemMessage(Component.literal(
                "§aAnnonce créée: §f" + toList.getHoverName().getString() +
                " ×" + pkt.quantity() + " → §e" + pkt.price() + " €"));

            AuctionOpener.sendSync(player);
        });
    }
}
