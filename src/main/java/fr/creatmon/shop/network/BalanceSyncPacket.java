package fr.creatmon.shop.network;

import fr.creatmon.shop.client.AuctionScreen;
import fr.creatmon.shop.client.ClientBalance;
import fr.creatmon.shop.client.ShopScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BalanceSyncPacket(int balance) implements CustomPacketPayload {

    public static final Type<BalanceSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("creatmon_gui", "balance_sync"));

    public static final StreamCodec<FriendlyByteBuf, BalanceSyncPacket> CODEC =
        StreamCodec.of((buf, pkt) -> buf.writeInt(pkt.balance()), buf -> new BalanceSyncPacket(buf.readInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BalanceSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientBalance.value       = pkt.balance();
            ClientBalance.initialized = true;
            // Sync the open menu so its displayed balance doesn't lag behind
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof ShopScreen s)
                s.getMenu().updateBalance(pkt.balance());
            else if (mc.screen instanceof AuctionScreen a)
                a.getMenu().updateBalance(pkt.balance());
            // Soft sync with cobblemon-silhouette's TrainerClientData.money
            syncSilhouetteMoney(pkt.balance());
        });
    }

    private static void syncSilhouetteMoney(int balance) {
        try {
            Class<?> cls = Class.forName("fr.natto.cobblemonsilhouette.TrainerClientData");
            java.lang.reflect.Field f = cls.getDeclaredField("money");
            f.setAccessible(true);
            f.setLong(null, balance);
        } catch (Exception ignored) {}
    }
}
