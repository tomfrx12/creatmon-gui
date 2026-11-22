package fr.creatmon.shop.lootbox;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class LootBoxBlockEntity extends BlockEntity {

    public LootBoxBlockEntity(BlockEntityType<LootBoxBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
