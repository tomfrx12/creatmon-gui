package fr.creatmon.shop.mixin.create;

import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import fr.creatmon.shop.pass.MachineXp;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Meule : compare l'inventaire de sortie avant/après. */
@Mixin(value = MillstoneBlockEntity.class, remap = false)
public abstract class MillstoneMixin {

    @Shadow public ItemStackHandler outputInv;
    @Unique private java.util.Map<net.minecraft.world.item.Item, Integer> creatmon$before;

    @Inject(method = "process", at = @At("HEAD"))
    private void creatmon$head(CallbackInfo ci) { creatmon$before = MachineXp.snapshot(outputInv, 0); }

    @Inject(method = "process", at = @At("TAIL"))
    private void creatmon$tail(CallbackInfo ci) {
        var be = (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
        MachineXp.recordDiff(be.getLevel(), be.getBlockPos(), creatmon$before, outputInv, 0);
    }

}
