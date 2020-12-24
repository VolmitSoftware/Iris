package com.volmit.iris.object;

import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an iris object placer. It places objects.")
@Data
public class IrisObjectPlacement extends IrisObjectPlacementOptions
{
	@RegistryListObject
	@Required
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("List of objects to place")
	private KList<String> place = new KList<>();

	public IrisObject getSchematic(DataProvider g, RNG random)
	{
		if(place.isEmpty())
		{
			return null;
		}

		return g.getData().getObjectLoader().load(place.get(random.nextInt(place.size())));
	}
}
