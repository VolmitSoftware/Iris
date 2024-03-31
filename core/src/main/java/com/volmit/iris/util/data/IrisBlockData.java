package com.volmit.iris.util.data;

import com.volmit.iris.core.link.Identifier;
import lombok.Data;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundGroup;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class IrisBlockData implements BlockData{
	private final @NonNull BlockData base;
	private final @NotNull Identifier custom;

	@NotNull
	@Override
	public Material getMaterial() {
		return base.getMaterial();
	}

	@NotNull
	@Override
	public String getAsString() {
		return base.getAsString();
	}

	@NotNull
	@Override
	public String getAsString(boolean b) {
		return base.getAsString(b);
	}

	@NotNull
	@Override
	public BlockData merge(@NotNull BlockData blockData) {
		return new IrisBlockData(base.merge(blockData), custom);
	}

	@Override
	public boolean matches(@Nullable BlockData blockData) {
		if (blockData instanceof IrisBlockData b)
			return custom.equals(b.custom) && base.matches(b.base);
		return base.matches(blockData);
	}

	@NotNull
	@Override
	public BlockData clone() {
		return new IrisBlockData(base.clone(), custom);
	}

	@NotNull
	@Override
	public SoundGroup getSoundGroup() {
		return base.getSoundGroup();
	}

	@Override
	public int getLightEmission() {
		return base.getLightEmission();
	}

	@Override
	public boolean isOccluding() {
		return base.isOccluding();
	}

	@Override
	public boolean requiresCorrectToolForDrops() {
		return base.requiresCorrectToolForDrops();
	}

	@Override
	public boolean isPreferredTool(@NotNull ItemStack itemStack) {
		return base.isPreferredTool(itemStack);
	}

	@NotNull
	@Override
	public PistonMoveReaction getPistonMoveReaction() {
		return base.getPistonMoveReaction();
	}

	@Override
	public boolean isSupported(@NotNull Block block) {
		return base.isSupported(block);
	}

	@Override
	public boolean isSupported(@NotNull Location location) {
		return base.isSupported(location);
	}

	@Override
	public boolean isFaceSturdy(@NotNull BlockFace blockFace, @NotNull BlockSupport blockSupport) {
		return base.isFaceSturdy(blockFace, blockSupport);
	}

	@NotNull
	@Override
	public Material getPlacementMaterial() {
		return base.getPlacementMaterial();
	}

	@Override
	public void rotate(@NotNull StructureRotation structureRotation) {
		base.rotate(structureRotation);
	}

	@Override
	public void mirror(@NotNull Mirror mirror) {
		base.mirror(mirror);
	}

	@NotNull
	@Override
	public BlockState createBlockState() {
		return base.createBlockState();
	}
}
