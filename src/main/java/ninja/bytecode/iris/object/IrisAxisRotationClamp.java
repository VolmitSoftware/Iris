package ninja.bytecode.iris.object;

import lombok.Data;

@Data
public class IrisAxisRotationClamp
{
	private boolean enabled = false;
	private double min = 0;
	private double max = 0;
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
