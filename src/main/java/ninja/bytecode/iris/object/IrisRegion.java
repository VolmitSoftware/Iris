package ninja.bytecode.iris.object;

import lombok.Data;
import ninja.bytecode.iris.util.KList;

@Data
public class IrisRegion
{
	private String name = "A Region";
	private double shoreRatio = 0.13;
	private double biomeImplosionRatio = 0.4;
	private KList<String> landBiomes = new KList<>();
	private KList<String> seaBiomes = new KList<>();
	private KList<String> shoreBiomes = new KList<>();
}
