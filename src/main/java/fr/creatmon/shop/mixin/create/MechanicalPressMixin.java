package fr.creatmon.shop.mixin.create;

import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import fr.creatmon.shop.pass.MachineXp;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Presse : pressage au sol, sur tapis ou sur dépôt. */
@Mixin(value = MechanicalPressBlockEntity.class, remap = false)
public abstract class MechanicalPressMixin {

    @Inject(method = "onItemPressed", at = @At("HEAD"))
    private void creatmon$onPressed(ItemStack result, CallbackInfo ci) {
        var be = (net.minecraft.world.level.block.entity.BlockEntity) (Object) this;
        MachineXp.record(be.getLevel(), be.getBlockPos(), result.getItem(), result.getCount());
    }
}
