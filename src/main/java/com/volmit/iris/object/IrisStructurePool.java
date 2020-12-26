package com.volmit.iris.object;

import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@DontObfuscate
@Desc("Represents a structure piece pool")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisStructurePool extends IrisRegistrant
{
	@RegistryListStructurePiece
	@Required
	@DontObfuscate
	@ArrayType(min = 1,type = String.class)
	@Desc("A list of structure piece pools")
	private KList<String> pieces = new KList<>();
}
