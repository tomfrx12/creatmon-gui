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
        registrar.playToServer(BuyItemPacket.TYPE,  BuyItemPacket.CODEC,  BuyItemPacket::handle);
        registrar.playToServer(SellItemPacket.TYPE, SellItemPacket.CODEC, SellItemPacket::handle);
    }
}
