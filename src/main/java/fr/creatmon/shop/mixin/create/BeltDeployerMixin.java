package fr.creatmon.shop.mixin.create;

import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import fr.creatmon.shop.pass.MachineXp;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Déployeur appliquant une recette sur un tapis / dépôt (assemblage séquencé inclus). */
@Mixin(value = BeltDeployerCallbacks.class, remap = false)
public abstract class BeltDeployerMixin {

    @Inject(method = "activate", at = @At("TAIL"))
    private static void creatmon$onActivate(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler,
                                            DeployerBlockEntity deployer, Recipe<?> recipe, CallbackInfo ci) {
        var be = (net.minecraft.world.level.block.entity.BlockEntity) (Object) deployer;
        var level = be.getLevel();
        if (level == null || recipe == null) return;
        var out = recipe.getResultItem(level.registryAccess());
        if (!out.isEmpty()) MachineXp.record(level, be.getBlockPos(), out.getItem(), out.getCount());
    }
}
