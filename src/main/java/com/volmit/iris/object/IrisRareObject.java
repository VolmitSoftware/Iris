package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RegistryListObject;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@DontObfuscate
@Desc("Represents a structure tile")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisRareObject
{

	@Required
	@MinNumber(1)
	@Desc("The rarity is 1 in X")
	@DontObfuscate
	private int rarity = 1;

	@RegistryListObject
	@Required
	@Desc("The object to place if rarity check passed")
	@DontObfuscate
	private String object = "";
}
