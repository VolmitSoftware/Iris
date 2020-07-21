package ninja.bytecode.iris.object;

import lombok.Data;
import ninja.bytecode.iris.util.Desc;

@Desc("Represents a rotation axis with intervals and maxes")
@Data
public class IrisAxisRotationClamp
{
	@Desc("Should this axis be rotated at all?")
	private boolean enabled = false;

	@Desc("The minimum angle (from) or set this and max to zero for any angle degrees")
	private double min = 0;

	@Desc("The maximum angle (to) or set this and min to zero for any angle degrees")
	private double max = 0;

	@Desc("Iris spins the axis but not freely. For example an interval of 90 would mean 4 possible angles (right angles) degrees")
	private double interval = 0;

	public IrisAxisRotationClamp()
	{

	}

	public IrisAxisRotationClamp(boolean enabled, double min, double max, double interval)
	{
		this.enabled = enabled;
		this.min = min;
		this.max = max;
		this.interval = interval;
	}

	public boolean isUnlimited()
	{
		return min == max;
	}

	public double getRadians(int rng)
	{
		if(isUnlimited())
		{
			return Math.toRadians((rng * interval) % 360D);
		}

		double deg = min + (rng * interval) % (Math.abs(max - min) / 360D);
		return Math.toRadians(deg);
	}
}
