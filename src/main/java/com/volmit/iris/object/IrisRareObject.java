package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RegistryListObject;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Builder
@AllArgsConstructor
@DontObfuscate
@Desc("Represents a structure tile")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisRareObject
{
	@Builder.Default
	@Required
	@MinNumber(1)
	@Desc("The rarity is 1 in X")
	@DontObfuscate
	private int rarity = 1;

	@Builder.Default
	@RegistryListObject
	@Required
	@Desc("The object to place if rarity check passed")
	@DontObfuscate
	private String object = "";

	public IrisRareObject()
	{

	}
}
