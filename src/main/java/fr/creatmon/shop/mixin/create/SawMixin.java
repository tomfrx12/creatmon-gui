package fr.creatmon.shop.mixin.create;

import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import fr.creatmon.shop.pass.MachineXp;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scie : items de sortie = slots 1+ de l'inventaire, comparés avant/après la recette. */
@Mixin(value = SawBlockEntity.class, remap = false)
public abstract class SawMixin {

    @Shadow public ProcessingInventory inventory;
    @Unique private java.util.Map<net.minecraft.world.item.Item, Integer> creatmon$before;

    @Inject(method = "applyRecipe", at = @At("HEAD"))
    private void creatmon$head(CallbackInfo ci) { creatmon$before = MachineXp.snapshot(inventory, 1); }

    @Inject(method = "applyRecipe", at = @At("TAIL"))
    private void creatmon$tail(CallbackInfo ci) {
        var be = (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
        MachineXp.recordDiff(be.getLevel(), be.getBlockPos(), creatmon$before, inventory, 1);
    }

}
