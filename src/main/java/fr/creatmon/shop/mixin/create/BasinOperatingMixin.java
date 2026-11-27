package fr.creatmon.shop.mixin.create;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import fr.creatmon.shop.pass.MachineXp;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Mixeur et presse (compactage) sur bassin. */
@Mixin(value = BasinOperatingBlockEntity.class, remap = false)
public abstract class BasinOperatingMixin {

    @Shadow protected Recipe<?> currentRecipe;

    @ModifyExpressionValue(method = "applyBasinRecipe", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/processing/basin/BasinRecipe;apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;)Z"))
    private boolean creatmon$onApplied(boolean applied) {
        if (applied && currentRecipe != null) {
            var be = (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
            var level = be.getLevel();
            if (level != null)
                {
                var out = currentRecipe.getResultItem(level.registryAccess());
                if (!out.isEmpty()) MachineXp.record(level, be.getBlockPos(), out.getItem(), out.getCount());
            }
        }
        return applied;
    }
}
