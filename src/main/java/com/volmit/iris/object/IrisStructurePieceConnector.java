package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.Required;
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
public class IrisStructurePieceConnector
{
	@Required
	@DontObfuscate
	@Desc("The name of this connector, such as entry, or table node. This is a name for organization, it has no effect on generation.")
	private String name = "";

	@DontObfuscate
	@Desc("Rotates the placed piece on this connector. If rotation is enabled, this connector will effectivley rotate, if this connector is facing the Z direction, then the connected piece would rotate in the X,Y direction in 90 degree segments.")
	private boolean rotateConnector = false;

	@DontObfuscate
	@Desc("If set to true, this connector is allowed to place pieces inside of it's own piece. For example if you are adding a light post, or house on top of a path piece, you would set this to true to allow the piece to collide with the path bounding box.")
	private boolean innerConnector = false;

	@DontObfuscate
	@Desc("The relative position this connector is located at for connecting to other pieces")
	@Required
	private IrisPosition position = new IrisPosition(0,0,0);

	@DontObfuscate
	@Desc("The direction this connector is facing. If the direction is set to UP, then pieces will place ABOVE the connector.")
	@Required
	private IrisDirection direction = IrisDirection.UP_POSITIVE_Y;
}
