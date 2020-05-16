package ninja.bytecode.iris.object;

import java.util.concurrent.locks.ReentrantLock;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisRegion extends IrisRegisteredObject
{
	private String name = "A Region";
	private double shoreRatio = 0.13;
	private double shoreHeightMin = 1.2;
	private double shoreHeightMax = 3.2;
	private double shoreHeightZoom = 3.14;
	private double biomeImplosionRatio = 0.4;
	private KList<String> landBiomes = new KList<>();
	private KList<String> seaBiomes = new KList<>();
	private KList<String> shoreBiomes = new KList<>();

	private transient CNG shoreHeightGenerator;
	private transient ReentrantLock lock = new ReentrantLock();

	public double getShoreHeight(double x, double z)
	{
		if(shoreHeightGenerator == null)
		{
			lock.lock();
			shoreHeightGenerator = CNG.signature(new RNG(hashCode()));
			lock.unlock();
		}

		return shoreHeightGenerator.fitDoubleD(shoreHeightMin, shoreHeightMax, x / shoreHeightZoom, z / shoreHeightZoom);
	}
}
