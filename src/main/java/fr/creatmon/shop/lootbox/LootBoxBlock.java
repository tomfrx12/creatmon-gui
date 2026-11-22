package fr.creatmon.shop.lootbox;

import com.mojang.serialization.MapCodec;
import fr.creatmon.shop.network.LootBoxOpenPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Supplier;

public class LootBoxBlock extends BaseEntityBlock {

    public static final MapCodec<LootBoxBlock> CODEC = simpleCodec(LootBoxBlock::new);

    private final Supplier<BlockEntityType<LootBoxBlockEntity>> entityTypeSupplier;

    public LootBoxBlock(BlockBehaviour.Properties props) {
        this(props, () -> null);
    }

    public LootBoxBlock(BlockBehaviour.Properties props, Supplier<BlockEntityType<LootBoxBlockEntity>> entityTypeSupplier) {
        super(props);
        this.entityTypeSupplier = entityTypeSupplier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LootBoxBlockEntity(entityTypeSupplier.get(), pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            openFor(sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            openFor(sp);
        }
        return ItemInteractionResult.SUCCESS;
    }

    private void openFor(ServerPlayer player) {
        // Déduit le type de chest depuis le nom registre : "chest_master_ball" → "master_ball"
        String blockId   = BuiltInRegistries.BLOCK.getKey(this).getPath(); // ex: chest_master_ball
        String chestType = blockId.startsWith("chest_") ? blockId.substring("chest_".length()) : blockId;

        // Compte la clé correspondante dans l'inventaire
        String keyId   = blockId + "_key"; // ex: chest_master_ball_key
        int    keyCount = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty()) {
                String itemId = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
                if (keyId.equals(itemId)) keyCount += s.getCount();
            }
        }

        PacketDistributor.sendToPlayer(player, new LootBoxOpenPacket(chestType, keyCount));
    }
}
