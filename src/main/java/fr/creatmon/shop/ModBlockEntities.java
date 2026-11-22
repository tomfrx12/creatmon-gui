package fr.creatmon.shop;

import fr.creatmon.shop.lootbox.LootBoxBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {

    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatmonShop.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LootBoxBlockEntity>> POKE_BALL =
            BLOCK_ENTITY_TYPES.register("chest_poke_ball",
                () -> BlockEntityType.Builder.of(
                    (pos, state) -> new LootBoxBlockEntity(ModBlockEntities.POKE_BALL.get(), pos, state),
                    ModBlocks.POKE_BALL.get()
                ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LootBoxBlockEntity>> MASTER_BALL =
            BLOCK_ENTITY_TYPES.register("chest_master_ball",
                () -> BlockEntityType.Builder.of(
                    (pos, state) -> new LootBoxBlockEntity(ModBlockEntities.MASTER_BALL.get(), pos, state),
                    ModBlocks.MASTER_BALL.get()
                ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LootBoxBlockEntity>> CREATE_BRONZE =
            BLOCK_ENTITY_TYPES.register("chest_create_bronze",
                () -> BlockEntityType.Builder.of(
                    (pos, state) -> new LootBoxBlockEntity(ModBlockEntities.CREATE_BRONZE.get(), pos, state),
                    ModBlocks.CREATE_BRONZE.get()
                ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LootBoxBlockEntity>> CREATE_GOLD =
            BLOCK_ENTITY_TYPES.register("chest_create_gold",
                () -> BlockEntityType.Builder.of(
                    (pos, state) -> new LootBoxBlockEntity(ModBlockEntities.CREATE_GOLD.get(), pos, state),
                    ModBlocks.CREATE_GOLD.get()
                ).build(null));

    static void register(IEventBus bus) {
        BLOCK_ENTITY_TYPES.register(bus);
    }
}
