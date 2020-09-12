package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

@Desc("An inventory slot type is used to represent a type of slot for items to fit into in any given inventory.")
public enum InventorySlotType
{
	@Desc("Typically the one you want to go with. Storage represnents most slots in inventories.")
	@DontObfuscate
	STORAGE,

	@Desc("Used for the fuel slot in Furnaces, Blast furnaces, smokers etc.")
	@DontObfuscate
	FUEL,

	@Desc("Used for the cook slot in furnaces")
	@DontObfuscate
	FURNACE,

	@Desc("Used for the cook slot in blast furnaces")
	@DontObfuscate
	BLAST_FURNACE,

	@Desc("Used for the cook slot in smokers")
	@DontObfuscate
	SMOKER,
}
