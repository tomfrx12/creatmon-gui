package fr.creatmon.shop.network;

import fr.creatmon.shop.CreatmonShop;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = CreatmonShop.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModPackets {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // Shop
        registrar.playToServer(BuyItemPacket.TYPE,  BuyItemPacket.CODEC,  BuyItemPacket::handle);
        registrar.playToServer(SellItemPacket.TYPE, SellItemPacket.CODEC, SellItemPacket::handle);
        // Auction House — client → server
        registrar.playToServer(AHBuyPacket.TYPE,    AHBuyPacket.CODEC,    AHBuyPacket::handle);
        registrar.playToServer(AHListPacket.TYPE,   AHListPacket.CODEC,   AHListPacket::handle);
        registrar.playToServer(AHCancelPacket.TYPE, AHCancelPacket.CODEC, AHCancelPacket::handle);
        registrar.playToServer(AHClaimPacket.TYPE,  AHClaimPacket.CODEC,  AHClaimPacket::handle);
        // Auction House — server → client
        registrar.playToClient(AHSyncPacket.TYPE,   AHSyncPacket.CODEC,   AHSyncPacket::handle);
        // Tableau de chasse — server → client
        registrar.playToClient(ChaseSyncPacket.TYPE,   ChaseSyncPacket.CODEC,   ChaseSyncPacket::handle);
        // Atelier Create — server → client
        registrar.playToClient(CraftSyncPacket.TYPE,   CraftSyncPacket.CODEC,   CraftSyncPacket::handle);
        // Balance HUD — server → client
        registrar.playToClient(BalanceSyncPacket.TYPE, BalanceSyncPacket.CODEC, BalanceSyncPacket::handle);
        // LootBox — server → client
        registrar.playToClient(LootBoxOpenPacket.TYPE,    LootBoxOpenPacket.CODEC,    LootBoxOpenPacket::handle);
        registrar.playToClient(LootBoxResultPacket.TYPE,  LootBoxResultPacket.CODEC,  LootBoxResultPacket::handle);
        registrar.playToClient(ItemBoxResultPacket.TYPE,  ItemBoxResultPacket.CODEC,  ItemBoxResultPacket::handle);
        // LootBox — client → server
        registrar.playToServer(LootBoxClaimPacket.TYPE,   LootBoxClaimPacket.CODEC,   LootBoxClaimPacket::handle);
    }
}
