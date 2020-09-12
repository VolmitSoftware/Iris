package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

@Desc("A loot mode is used to descrive what to do with the existing loot layers before adding this loot. Using ADD will simply add this table to the building list of tables (i.e. add dimension tables, region tables then biome tables). By using clear or replace, you remove the parent tables before and add just your tables.")
public enum LootMode
{
	@Desc("Add to the existing parent loot tables")
	@DontObfuscate
	ADD,

	@Desc("Clear all loot tables then add this table")
	@DontObfuscate
	CLEAR,

	@Desc("Replace all loot tables with this table (same as clear)")
	@DontObfuscate
	REPLACE;
}
