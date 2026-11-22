package fr.creatmon.shop;

import fr.creatmon.shop.lootbox.LootBoxBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, CreatmonShop.MOD_ID);

    private static BlockBehaviour.Properties lootBoxProps() {
        return BlockBehaviour.Properties.of().noOcclusion().strength(1.5f).noLootTable();
    }

    public static final DeferredHolder<Block, LootBoxBlock> POKE_BALL =
            BLOCKS.register("chest_poke_ball",
                () -> new LootBoxBlock(lootBoxProps(), () -> ModBlockEntities.POKE_BALL.get()));

    public static final DeferredHolder<Block, LootBoxBlock> MASTER_BALL =
            BLOCKS.register("chest_master_ball",
                () -> new LootBoxBlock(lootBoxProps(), () -> ModBlockEntities.MASTER_BALL.get()));

    public static final DeferredHolder<Block, LootBoxBlock> CREATE_BRONZE =
            BLOCKS.register("chest_create_bronze",
                () -> new LootBoxBlock(lootBoxProps(), () -> ModBlockEntities.CREATE_BRONZE.get()));

    public static final DeferredHolder<Block, LootBoxBlock> CREATE_GOLD =
            BLOCKS.register("chest_create_gold",
                () -> new LootBoxBlock(lootBoxProps(), () -> ModBlockEntities.CREATE_GOLD.get()));

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
