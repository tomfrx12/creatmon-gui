package fr.creatmon.shop.pass;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Qui a posé chaque bloc du mod Create, par dimension
 * (sauvegardé dans data/creatmon_machine_owners.dat du monde).
 */
public class MachineOwners extends SavedData {

    private static final String NAME = "creatmon_machine_owners";
    private final Map<Long, UUID> owners = new HashMap<>();

    public static MachineOwners get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(MachineOwners::new, MachineOwners::load, null), NAME);
    }

    public void set(BlockPos pos, UUID owner) { owners.put(pos.asLong(), owner); setDirty(); }

    public void remove(BlockPos pos) { if (owners.remove(pos.asLong()) != null) setDirty(); }

    /**
     * Propriétaire de la machine à {@code pos}. Cherche aussi autour (rayon 1, et jusqu'à 2 blocs
     * au-dessus) car certaines machines sont des blocs multiples (roues de broyage, presse/mixeur sur bassin).
     */
    public static UUID find(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel sl)) return null;
        MachineOwners data = get(sl);
        UUID u = data.owners.get(pos.asLong());
        if (u != null) return u;
        for (int dy = 1; dy <= 2; dy++) {
            u = data.owners.get(pos.above(dy).asLong());
            if (u != null) return u;
        }
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            u = data.owners.get(p.asLong());
            if (u != null) return u;
        }
        return null;
    }

    private static MachineOwners load(CompoundTag tag, HolderLookup.Provider provider) {
        MachineOwners d = new MachineOwners();
        for (Tag t : tag.getList("owners", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.owners.put(c.getLong("pos"), c.getUUID("owner"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        owners.forEach((pos, owner) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", pos);
            c.putUUID("owner", owner);
            list.add(c);
        });
        tag.put("owners", list);
        return tag;
    }
}
