package com.volmit.iris.object.tile;

import lombok.Data;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Data
public class TileSpawner implements TileData<CreatureSpawner> {
    public static final int id = 1;
    private EntityType entityType;

    @Override
    public String getTileId() {
        return "minecraft:spawner";
    }

    @Override
    public boolean isApplicable(BlockData data) {
        return data.getMaterial() == Material.SPAWNER;
    }

    @Override
    public void toBukkit(CreatureSpawner t) {
        t.setSpawnedType(entityType);
    }

    @Override
    public void fromBukkit(CreatureSpawner sign) {
        entityType = sign.getSpawnedType();
    }

    @Override
    public TileSpawner clone() {
        TileSpawner ts = new TileSpawner();
        ts.setEntityType(getEntityType());
        return ts;
    }

    @Override
    public void toBinary(DataOutputStream out) throws IOException {
        out.writeShort(id);
        out.writeShort(entityType.ordinal());
    }

    @Override
    public void fromBinary(DataInputStream in) throws IOException {
        entityType = EntityType.values()[in.readShort()];
    }

    @Override
    public void toNBT(CompoundTag tag) {
        ListTag<CompoundTag> potentials = (ListTag<CompoundTag>) ListTag.createUnchecked(CompoundTag.class);
        CompoundTag t = new CompoundTag();
        CompoundTag ent = new CompoundTag();
        ent.putString("id", entityType.getKey().toString());
        t.put("Entity", ent);
        t.putInt("Weight", 1);
        potentials.add(t);
        tag.put("SpawnPotentials", potentials);
    }
}
