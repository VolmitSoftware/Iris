package ninja.bytecode.iris;

import lombok.Data;
import ninja.bytecode.iris.util.RollingSequence;

@Data
public class IrisMetrics
{
	private final RollingSequence total;
	
	public IrisMetrics(int memory)
	{
		total = new RollingSequence(memory);
	}	
}
