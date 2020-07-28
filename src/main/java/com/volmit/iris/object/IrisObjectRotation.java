package ninja.bytecode.iris.object;

import org.bukkit.util.BlockVector;

import lombok.Data;
import ninja.bytecode.iris.util.Desc;

@Desc("Configures rotation for iris")
@Data
public class IrisObjectRotation
{
	@Desc("If this rotator is enabled or not")
	private boolean enabled = true;
	@Desc("The x axis rotation")
	private IrisAxisRotationClamp xAxis = new IrisAxisRotationClamp();
	@Desc("The y axis rotation")
	private IrisAxisRotationClamp yAxis = new IrisAxisRotationClamp(true, 0, 0, 90);
	@Desc("The z axis rotation")
	private IrisAxisRotationClamp zAxis = new IrisAxisRotationClamp();

	public IrisObjectRotation()
	{

	}

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

	public BlockVector rotate(BlockVector b, boolean yf, boolean xf, int spinx, int spiny, int spinz)
	{
		if(!canRotate())
		{
			return b;
		}

		BlockVector v = b.clone();

		if(yf && canRotateY())
		{
			v.rotateAroundY(getYRotation(spiny));
		}

		if(xf && canRotateX())
		{
			v.rotateAroundX(getXRotation(spinx));
		}

		if(canRotateZ())
		{
			v.rotateAroundZ(getZRotation(spinz));
		}

		if(!xf && canRotateX())
		{
			v.rotateAroundX(getXRotation(spinx));
		}

		if(!yf && canRotateY())
		{
			v.rotateAroundY(getYRotation(spiny));
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
