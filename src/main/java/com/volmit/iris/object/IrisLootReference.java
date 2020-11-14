package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RegistryListLoot;

import com.volmit.iris.scaffold.data.DataProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a loot entry")
@Data
public class IrisLootReference
{

	@DontObfuscate
	@Desc("Add = add on top of parent tables, Replace = clear first then add these. Clear = Remove all and dont add loot from this or parent.")
	private LootMode mode = LootMode.ADD;

	@DontObfuscate
	@RegistryListLoot
	@ArrayType(min = 1, type = String.class)
	@Desc("Add loot table registries here")
	private KList<String> tables = new KList<>();

	@MinNumber(0)
	@DontObfuscate
	@Desc("Increase the chance of loot in this area")
	private double multiplier = 1D;

	private final transient AtomicCache<KList<IrisLootTable>> tt = new AtomicCache<>();

	public KList<IrisLootTable> getLootTables(DataProvider g)
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
