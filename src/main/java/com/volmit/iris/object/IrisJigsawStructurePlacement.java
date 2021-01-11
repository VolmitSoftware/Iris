package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.RegistryListJigsaw;
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
@Desc("Represents a jigsaw structure placer")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisJigsawStructurePlacement extends IrisRegistrant
{
	@RegistryListJigsaw
	@Required
	@DontObfuscate
	@Desc("The structure to place")
	private String structure;

	@DontObfuscate
	@Required
	@Desc("The 1 in X chance rarity")
	private int rarity = 100;
}
