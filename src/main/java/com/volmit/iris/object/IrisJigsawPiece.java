package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.util.BlockVector;

import java.io.IOException;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@DontObfuscate
@Desc("Represents a structure tile")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisJigsawPiece extends IrisRegistrant
{
	@RegistryListObject
	@Required
	@DontObfuscate
	@Desc("The object this piece represents")
	private String object = "";

	@Required
	@DontObfuscate
	@ArrayType(type = IrisJigsawPieceConnector.class, min = 1)
	@Desc("The connectors this object contains")
	private KList<IrisJigsawPieceConnector> connectors = new KList<>();

	@Desc("Configure everything about the object placement. Please don't define this unless you actually need it as using this option will slow down the jigsaw deign stage. Use this where you need it, just avoid using it everywhere to keep things fast.")
	@DontObfuscate
	private IrisObjectPlacement placementOptions = new IrisObjectPlacement().setMode(ObjectPlaceMode.FAST_MAX_HEIGHT);

	private transient AtomicCache<Integer> max2dDim = new AtomicCache<>();
	private transient AtomicCache<Integer> max3dDim = new AtomicCache<>();

	public int getMax2dDimension() {
		return max2dDim.aquire(() -> {
			try {
				BlockVector v = IrisObject.sampleSize(getLoader().getObjectLoader().findFile(getObject()));
				return Math.max(v.getBlockX(), v.getBlockZ());
			} catch (IOException e) {
				e.printStackTrace();
			}

			return 0;
		});
	}

	public int getMax3dDimension() {
		return max3dDim.aquire(() -> {
			try {
				BlockVector v = IrisObject.sampleSize(getLoader().getObjectLoader().findFile(getObject()));
				return Math.max(Math.max(v.getBlockX(), v.getBlockZ()), v.getBlockY());
			} catch (IOException e) {
				e.printStackTrace();
			}

			return -1;
		});
	}



	public IrisJigsawPieceConnector getConnector(IrisPosition relativePosition) {
		for(IrisJigsawPieceConnector i : connectors)
		{
			if(i.getPosition().equals(relativePosition))
			{
				return i;
			}
		}

		return null;
	}

	public IrisJigsawPiece copy() {
		IrisJigsawPiece p = new IrisJigsawPiece();
		p.setObject(getObject());
		p.setLoader(getLoader());
		p.setLoadKey(getLoadKey());
		p.setLoadFile(getLoadFile());
		p.setConnectors(new KList<>());
		p.setPlacementOptions(getPlacementOptions());

		for(IrisJigsawPieceConnector i : getConnectors())
		{
			p.getConnectors().add(i.copy());
		}

		return p;
	}

	public boolean isTerminal() {
		return connectors.size() == 1;
	}

	public ObjectPlaceMode getPlaceMode() {
		return getPlacementOptions().getMode();
	}
}
