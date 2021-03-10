package com.volmit.iris.object;

import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@DontObfuscate
@Desc("Represents a structure tile")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisJigsawPieceConnector
{
	@Required
	@DontObfuscate
	@Desc("The name of this connector, such as entry, or table node. This is a name for organization. Other connectors can specifically use targetName to target a specific connector type. Multiple connectors can use the same name.")
	private String name = "";

	@Required
	@DontObfuscate
	@Desc("Target a piece's connector with the specified name. For any piece's connector, define * or don't define it.")
	private String targetName = "*";

	@DontObfuscate
	@Desc("Rotates the placed piece on this connector. If rotation is enabled, this connector will effectivley rotate, if this connector is facing the Z direction, then the connected piece would rotate in the X,Y direction in 90 degree segments.")
	private boolean rotateConnector = false;

	@DontObfuscate
	@Desc("If set to true, this connector is allowed to place pieces inside of it's own piece. For example if you are adding a light post, or house on top of a path piece, you would set this to true to allow the piece to collide with the path bounding box.")
	private boolean innerConnector = false;

	@RegistryListJigsawPool
	@Desc("Pick piece pools to place onto this connector")
	@ArrayType(type = String.class, min = 1)
	@Required
	private KList<String> pools = new KList<>();

	@RegistryListEntity
	@DontObfuscate
	@Desc("Pick an entity to spawn on this connector")
	private String spawnEntity;

	@DontObfuscate
	@MaxNumber(50)
	@MinNumber(1)
	@Desc("The amount of entities to spawn (must be a whole number)")
	private int entityCount = 1;

	@DontObfuscate
	@Desc("The relative position this connector is located at for connecting to other pieces")
	@Required
	private IrisPosition position = new IrisPosition(0,0,0);

	@DontObfuscate
	@Desc("The relative position to this connector to place entities at")
	@DependsOn({"spawnEntity"})
	private IrisPosition entityPosition = null;

	@DontObfuscate
	@Desc("The direction this connector is facing. If the direction is set to UP, then pieces will place ABOVE the connector.")
	@Required
	private IrisDirection direction = IrisDirection.UP_POSITIVE_Y;

	public String toString()
	{
		return direction.getFace().name() + "@(" + position.getX() + "," + position.getY() + "," + position.getZ() + ")";
	}

	public IrisJigsawPieceConnector copy() {
		IrisJigsawPieceConnector c = new IrisJigsawPieceConnector();
		c.setInnerConnector(isInnerConnector());
		c.setTargetName(getTargetName());
		c.setPosition(getPosition().copy());
		c.setDirection(getDirection());
		c.setRotateConnector(isRotateConnector());
		c.setName(getName());
		c.setSpawnEntity(getSpawnEntity());
		c.setPools(getPools().copy());
		return c;
	}
}
