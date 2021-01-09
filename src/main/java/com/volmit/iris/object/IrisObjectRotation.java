package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.*;
import org.bukkit.util.BlockVector;

import java.util.List;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Configures rotation for iris")
@Data
public class IrisObjectRotation
{
	@DontObfuscate
	@Desc("If this rotator is enabled or not")
	private boolean enabled = true;

	@DontObfuscate
	@Desc("The x axis rotation")
	private IrisAxisRotationClamp xAxis = new IrisAxisRotationClamp();

	@DontObfuscate
	@Desc("The y axis rotation")
	private IrisAxisRotationClamp yAxis = new IrisAxisRotationClamp(true, 0, 0, 90);

	@DontObfuscate
	@Desc("The z axis rotation")
	private IrisAxisRotationClamp zAxis = new IrisAxisRotationClamp();

	public double getYRotation(int spin)
	{
		return getRotation(spin, yAxis);
	}

	public double getXRotation(int spin)
	{
		return getRotation(spin, xAxis);
	}

	public double getZRotation(int spin)
	{
		return getRotation(spin, zAxis);
	}

	public IrisObject rotateCopy(IrisObject e)
	{
		return e.rotateCopy(this);
	}


	public IrisJigsawPiece rotateCopy(IrisJigsawPiece v) {
		IrisJigsawPiece piece = v.copy();
		for(IrisJigsawPieceConnector i : piece.getConnectors())
		{
			i.setPosition(rotate(i.getPosition()));
			i.setDirection(rotate(i.getDirection()));
		}

		return piece;
	}


	public BlockVector rotate(BlockVector direction) {
		return rotate(direction, 0,0,0);
	}

	public IrisDirection rotate(IrisDirection direction) {
		BlockVector v = rotate(direction.toVector().toBlockVector());
		return IrisDirection.closest(v);
	}

	public static IrisObjectRotation of(double x, double y, double z) {
		IrisObjectRotation rt = new IrisObjectRotation();
		rt.setEnabled(true);
		IrisAxisRotationClamp rtx = new IrisAxisRotationClamp();
		rtx.setEnabled(x != 0);
		rtx.setMax(x);
		rt.setXAxis(rtx);
		IrisAxisRotationClamp rty = new IrisAxisRotationClamp();
		rty.setEnabled(y != 0);
		rty.setMax(y);
		rt.setXAxis(rty);
		IrisAxisRotationClamp rtz = new IrisAxisRotationClamp();
		rtz.setEnabled(z != 0);
		rtz.setMax(z);
		rt.setXAxis(rtz);
		return rt;
	}

	public double getRotation(int spin, IrisAxisRotationClamp clamp)
	{
		if(!enabled)
		{
			return 0;
		}

		if(!clamp.isEnabled())
		{
			return 0;
		}

		return clamp.getRadians(spin);
	}

	public BlockFace getFace(BlockVector v)
	{
		int x = (int) Math.round(v.getX());
		int y = (int) Math.round(v.getY());
		int z = (int) Math.round(v.getZ());

		if(x == 0 && z == -1)
		{
			return BlockFace.NORTH;
		}

		if(x == 0 && z == 1)
		{
			return BlockFace.SOUTH;
		}

		if(x == 1 && z == 0)
		{
			return BlockFace.EAST;
		}

		if(x == -1 && z == 0)
		{
			return BlockFace.WEST;
		}

		if(y > 0)
		{
			return BlockFace.UP;
		}

		if(y < 0)
		{
			return BlockFace.DOWN;
		}

		return BlockFace.SOUTH;
	}

	public BlockFace faceForAxis(Axis axis)
	{
		switch(axis)
		{
			case X:
				return BlockFace.EAST;
			case Y:
				return BlockFace.UP;
			case Z:
				return BlockFace.NORTH;
		}

		return BlockFace.NORTH;
	}

	public Axis axisFor(BlockFace f)
	{
		switch(f)
		{
			case NORTH:
			case SOUTH:
				return Axis.Z;
			case EAST:
			case WEST:
				return Axis.X;
			case UP:
			case DOWN:
				return Axis.Y;
		}

		return Axis.Y;
	}

	public Axis axisFor2D(BlockFace f)
	{
		switch(f)
		{
			case NORTH:
			case SOUTH:
				return Axis.Z;
			case EAST:
			case WEST:
			case UP:
			case DOWN:
				return Axis.X;
		}

		return Axis.Z;
	}

	public BlockData rotate(BlockData dd, int spinxx, int spinyy, int spinzz)
	{
		BlockData d = dd;
		int spinx = (int) (90D * (Math.ceil(Math.abs((spinxx % 360D) / 90D))));
		int spiny = (int) (90D * (Math.ceil(Math.abs((spinyy % 360D) / 90D))));
		int spinz = (int) (90D * (Math.ceil(Math.abs((spinzz % 360D) / 90D))));

		if(!canRotate())
		{
			return d;
		}

		if(d instanceof Directional)
		{
			Directional g = ((Directional) d);
			BlockFace f = g.getFacing();
			BlockVector bv = new BlockVector(f.getModX(), f.getModY(), f.getModZ());
			bv = rotate(bv.clone(), spinx, spiny, spinz);
			BlockFace t = getFace(bv);

			if(g.getFaces().contains(t))
			{
				g.setFacing(t);
			}

			else if(!g.getMaterial().isSolid())
			{
				d = null;
			}
		}

		else if(d instanceof Rotatable)
		{
			Rotatable g = ((Rotatable) d);
			BlockFace f = g.getRotation();
			BlockVector bv = new BlockVector(f.getModX(), f.getModY(), f.getModZ());
			bv = rotate(bv.clone(), spinx, spiny, spinz);
			BlockFace t = getFace(bv);
			g.setRotation(t);
		}

		else if(d instanceof MultipleFacing)
		{
			List<BlockFace> faces = new KList<>();
			MultipleFacing g = (MultipleFacing) d;

			for(BlockFace i : g.getFaces())
			{
				BlockVector bv = new BlockVector(i.getModX(), i.getModY(), i.getModZ());
				bv = rotate(bv.clone(), spinx, spiny, spinz);
				BlockFace r = getFace(bv);

				if(g.getAllowedFaces().contains(r))
				{
					faces.add(r);
				}
			}

			for(BlockFace i : g.getFaces())
			{
				g.setFace(i, false);
			}

			for(BlockFace i : faces)
			{
				g.setFace(i, true);
			}
		}

		else if(d.getMaterial().equals(Material.NETHER_PORTAL) && d instanceof Orientable)
		{
			//TODO: Fucks up logs
			Orientable g = ((Orientable) d);
			BlockFace f = faceForAxis(g.getAxis());
			BlockVector bv = new BlockVector(f.getModX(), f.getModY(), f.getModZ());
			bv = rotate(bv.clone(), spinx, spiny, spinz);
			BlockFace t = getFace(bv);
			Axis a = !g.getAxes().contains(Axis.Y) ? axisFor(t) : axisFor2D(t);
			((Orientable) d).setAxis(a);
		}

		return d;
	}

	public IrisPosition rotate(IrisPosition b)
	{
		return rotate(b, 0,0,0);
	}

	public IrisPosition rotate(IrisPosition b, int spinx, int spiny, int spinz)
	{
		return new IrisPosition(rotate(new BlockVector(b.getX(), b.getY(), b.getZ()), spinx, spiny, spinz));
	}

	public BlockVector rotate(BlockVector b, int spinx, int spiny, int spinz)
	{
		if(!canRotate())
		{
			return b;
		}

		BlockVector v = b.clone();

		if(canRotateX())
		{
			v.rotateAroundX(getXRotation(spinx));
		}

		if(canRotateZ())
		{
			v.rotateAroundZ(getZRotation(spinz));
		}

		if(canRotateY())
		{
			if(getYAxis().isLocked())
			{
				if(Math.abs(getYAxis().getMax()) == 180D)
				{
					v.setX(-v.getX());
					v.setZ(-v.getZ());
				}

				else if(getYAxis().getMax() == 90D || getYAxis().getMax() == -270D)
				{
					double x = v.getX();
					v.setX(v.getZ());
					v.setZ(-x);
				}

				else if(getYAxis().getMax() == -90D || getYAxis().getMax() == 270D)
				{
					double x = v.getX();
					v.setX(-v.getZ());
					v.setZ(x);
				}

				else
				{
					v.rotateAroundY(getYRotation(spiny));
				}
			}

			else
			{
				v.rotateAroundY(getYRotation(spiny));
			}
		}

		return v;
	}

	public boolean canRotateX()
	{
		return enabled && xAxis.isEnabled();
	}

	public boolean canRotateY()
	{
		return enabled && yAxis.isEnabled();
	}

	public boolean canRotateZ()
	{
		return enabled && zAxis.isEnabled();
	}

	public boolean canRotate()
	{
		return canRotateX() || canRotateY() || canRotateZ();
	}
}
