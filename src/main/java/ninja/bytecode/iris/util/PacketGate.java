package ninja.bytecode.iris.util;

import mortar.compute.math.RollingAverage;
import mortar.lang.collection.GMap;

public class PacketGate
{
	private static final GMap<PacketCategory, PacketGate> gates = new GMap<>();
	private final RollingAverage average;
	private final int pps;
	private int sent;

	private PacketGate(int pps)
	{
		sent = 0;
		this.pps = pps;
		average = new RollingAverage(100);
	}

	public void tick()
	{
		average.put(sent * 20D);
		sent = 0;
	}

	public boolean can()
	{
		if(should())
		{
			mark();
			return true;
		}

		return false;
	}

	public boolean should()
	{
		if(average.get() < pps)
		{
			return true;
		}

		return false;
	}

	public void mark()
	{
		sent++;
	}

	private double getAveragePPS()
	{
		return average.get();
	}

	public static int getTotalPPS()
	{
		double m = 0;

		for(PacketCategory i : PacketCategory.values())
		{
			m += getAveragePPS(i);
		}

		return (int) m;
	}

	public static void tickAll()
	{
		if(gates.isEmpty())
		{
			reset();
		}

		for(PacketCategory i : PacketCategory.values())
		{
			gates.get(i).tick();
		}
	}

	public static double getAveragePPS(PacketCategory cat)
	{
		if(!gates.containsKey(cat))
		{
			reset();
		}

		return gates.get(cat).getAveragePPS();
	}

	public static boolean can(PacketCategory cat)
	{
		if(!gates.containsKey(cat))
		{
			reset();
		}

		return gates.get(cat).can();
	}

	public static void mark(PacketCategory cat)
	{
		if(!gates.containsKey(cat))
		{
			reset();
		}

		gates.get(cat).mark();
	}

	public static boolean should(PacketCategory cat)
	{
		if(!gates.containsKey(cat))
		{
			reset();
		}

		return gates.get(cat).should();
	}

	public static void reset()
	{
		gates.put(PacketCategory.BOARD, new PacketGate(100));
		gates.put(PacketCategory.EFFECT, new PacketGate(100));
		gates.put(PacketCategory.HOLOGRAM, new PacketGate(100));
		gates.put(PacketCategory.TABLIST, new PacketGate(100));
	}
}
