package com.volmit.iris.object;

import org.bukkit.Material;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.util.B;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.Required;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Desc("Find and replace object items for compatability")
@Data
public class IrisCompatabilityItemFilter
{
	@Required
	@DontObfuscate
	@Desc("When iris sees this block, and it's not reconized")
	private String when = "";

	@Required
	@DontObfuscate
	@Desc("Replace it with this block. Dont worry if this block is also not reconized, iris repeat this compat check.")
	private String supplement = "";

	private final transient AtomicCache<Material> findData = new AtomicCache<>(true);
	private final transient AtomicCache<Material> replaceData = new AtomicCache<>(true);

	public IrisCompatabilityItemFilter(String when, String supplement)
	{
		this.when = when;
		this.supplement = supplement;
	}

	public Material getFind()
	{
		return findData.aquire(() -> B.getMaterial(when));
	}

	public Material getReplace()
	{
		return replaceData.aquire(() ->
		{
			Material b = B.getMaterialOrNull(supplement);

			if(b == null)
			{
				return null;
			}
			
			Iris.verbose("Compat: Using " + supplement + " in place of " + when + " since this server doesnt support '" + supplement + "'");

			return b;
		});
	}
}
