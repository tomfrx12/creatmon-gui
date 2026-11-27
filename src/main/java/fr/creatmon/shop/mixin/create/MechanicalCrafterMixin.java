package fr.creatmon.shop.mixin.create;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import fr.creatmon.shop.pass.MachineXp;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Crafteur mécanique. */
@Mixin(value = MechanicalCrafterBlockEntity.class, remap = false)
public abstract class MechanicalCrafterMixin {

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/kinetics/crafter/RecipeGridHandler;tryToApplyRecipe(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/kinetics/crafter/RecipeGridHandler$GroupedItems;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack creatmon$onCraft(ItemStack result) {
        if (result != null && !result.isEmpty()) {
            var be = (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
            MachineXp.record(be.getLevel(), be.getBlockPos(), result.getItem(), result.getCount());
        }
        return result;
    }
}
