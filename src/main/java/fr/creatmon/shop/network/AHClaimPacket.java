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

import java.util.ArrayList;
import java.util.List;

public record AHClaimPacket(int index) implements CustomPacketPayload {

    /** index = -1 → tout récupérer ; index >= 0 → item spécifique */
    public AHClaimPacket() { this(-1); }

    public static final Type<AHClaimPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "ah_claim"));

    public static final StreamCodec<FriendlyByteBuf, AHClaimPacket> CODEC =
        StreamCodec.of((buf, pkt) -> buf.writeInt(pkt.index()), buf -> new AHClaimPacket(buf.readInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AHClaimPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            List<ItemStack> inbox = AuctionData.getInboxItems(player.getUUID());
            if (inbox.isEmpty()) return;

            if (pkt.index() >= 0) {
                // Récupérer un seul item
                if (pkt.index() >= inbox.size()) return;
                ItemStack item = inbox.get(pkt.index());
                if (!player.addItem(item.copy())) {
                    player.sendSystemMessage(Component.literal("§e[AH] Inventaire plein."));
                    return;
                }
                List<ItemStack> remaining = new ArrayList<>(inbox);
                remaining.remove(pkt.index());
                AuctionData.setInboxItems(player.getUUID(), remaining);
                player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.0f);
            } else {
                // Tout récupérer
                List<ItemStack> remaining = new ArrayList<>();
                int claimed = 0;
                for (ItemStack item : inbox) {
                    if (!player.addItem(item.copy())) remaining.add(item);
                    else claimed++;
                }
                AuctionData.setInboxItems(player.getUUID(), remaining);
                player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.0f);
                if (claimed > 0)
                    player.sendSystemMessage(Component.literal("§a[AH] " + claimed + " item(s) récupéré(s)."));
                if (!remaining.isEmpty())
                    player.sendSystemMessage(Component.literal("§e[AH] " + remaining.size() + " item(s) non récupéré(s) — inventaire plein."));
            }

            AuctionOpener.sendSync(player);
        });
    }
}
