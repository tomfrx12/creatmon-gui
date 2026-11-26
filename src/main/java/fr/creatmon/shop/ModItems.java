package fr.creatmon.shop;

import fr.creatmon.shop.lootbox.KeyItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, CreatmonShop.MOD_ID);

    public static final DeferredHolder<Item, BlockItem> CHEST_POKE_BALL =
            ITEMS.register("chest_poke_ball",
                () -> new BlockItem(ModBlocks.POKE_BALL.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> CHEST_MASTER_BALL =
            ITEMS.register("chest_master_ball",
                () -> new BlockItem(ModBlocks.MASTER_BALL.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> CHEST_CREATE_BRONZE =
            ITEMS.register("chest_create_bronze",
                () -> new BlockItem(ModBlocks.CREATE_BRONZE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> CHEST_CREATE_GOLD =
            ITEMS.register("chest_create_gold",
                () -> new BlockItem(ModBlocks.CREATE_GOLD.get(), new Item.Properties()));

    public static final DeferredHolder<Item, KeyItem> POKE_BALL_KEY =
            ITEMS.register("chest_poke_ball_key", KeyItem::new);

    public static final DeferredHolder<Item, KeyItem> MASTER_BALL_KEY =
            ITEMS.register("chest_master_ball_key", KeyItem::new);

    public static final DeferredHolder<Item, KeyItem> CREATE_BRONZE_KEY =
            ITEMS.register("chest_create_bronze_key", KeyItem::new);

    public static final DeferredHolder<Item, KeyItem> CREATE_GOLD_KEY =
            ITEMS.register("chest_create_gold_key", KeyItem::new);

    static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
