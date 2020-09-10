package com.volmit.iris.object;

import com.volmit.iris.gen.DimensionalTerrainProvider;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RegistryListLoot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@AllArgsConstructor
@Desc("Represents a loot entry")
@Data
public class IrisLootReference
{
	@Builder.Default
	@DontObfuscate
	@Desc("Add = add on top of parent tables, Replace = clear first then add these. Clear = Remove all and dont add loot from this or parent.")
	private LootMode mode = LootMode.ADD;

	@Builder.Default
	@DontObfuscate
	@RegistryListLoot
	@ArrayType(min = 1, type = String.class)
	@Desc("Add loot table registries here")
	private KList<String> tables = new KList<>();

	@Builder.Default
	@MinNumber(0)
	@DontObfuscate
	@Desc("Increase the chance of loot in this area")
	private double multiplier = 1D;

	private final transient AtomicCache<KList<IrisLootTable>> tt = new AtomicCache<>();

	public IrisLootReference()
	{

	}

	public KList<IrisLootTable> getLootTables(DimensionalTerrainProvider g)
	{
		return tt.aquire(() ->
		{
			KList<IrisLootTable> t = new KList<>();

			for(String i : tables)
			{
				t.add(g.getData().getLootLoader().load(i));
			}

			return t;
		});
	}
}
