package ninja.bytecode.iris;

import java.util.function.Consumer;

import org.bukkit.entity.Player;

import mortar.compute.math.M;
import mortar.util.text.C;
import ninja.bytecode.iris.controller.ExecutionController;
import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.format.F;
import ninja.bytecode.shuriken.math.RollingSequence;

public class IrisMetrics
{
	private int size;
	private int generators;
	private double scale;
	private GMap<String, RollingSequence> sequences;

	public IrisMetrics(int generators, int size)
	{
		scale = 1;
		this.size = size;
		this.generators = generators;
		sequences = new GMap<>();
	}

	public String avgMS(String s, int dec)
	{
		return F.duration(get(s).getAverage(), dec);
	}

	public String avg(String s, int dec)
	{
		return F.f(get(s).getAverage(), dec);
	}

	public String maxMS(String s, int dec)
	{
		return F.duration(get(s).getMax(), dec);
	}

	public String max(String s, int dec)
	{
		return F.f(get(s).getMax(), dec);
	}

	public String minMS(String s, int dec)
	{
		return F.duration(get(s).getMin(), dec);
	}

	public String min(String s, int dec)
	{
		return F.f(get(s).getMin(), dec);
	}

	public String medianMS(String s, int dec)
	{
		return F.duration(get(s).getMedian(), dec);
	}

	public String median(String s, int dec)
	{
		return F.f(get(s).getMedian(), dec);
	}

	public RollingSequence get(String s)
	{
		if(!sequences.containsKey(s))
		{
			return new RollingSequence(2);
		}

		return sequences.get(s);
	}

	public PrecisionStopwatch start()
	{
		return PrecisionStopwatch.start();
	}

	public void stop(String f, PrecisionStopwatch g)
	{
		put(f, g.getMilliseconds());
	}

	public void put(String f, double t)
	{
		if(!sequences.containsKey(f))
		{
			sequences.put(f, new RollingSequence(size));
		}

		sequences.get(f).put(t);
	}

	public int getGenerators()
	{
		return generators;
	}

	public void setGenerators(int generators)
	{
		this.generators = generators;
	}

	public GMap<String, RollingSequence> getSequences()
	{
		return sequences;
	}

	public void setSequences(GMap<String, RollingSequence> sequences)
	{
		this.sequences = sequences;
	}

	public void send(Player p, Consumer<String> c)
	{
		send(p, c, null, 0);
	}

	public void setParScale(double sf)
	{
		scale = sf;
	}

	public void send(Player p, Consumer<String> c, String parent, int ind)
	{
		GMap<String, String> out = new GMap<>();

		looking: for(String i : getSequences().k())
		{
			GList<String> o = new GList<>();

			if(i.contains(":"))
			{
				o.add(i.split("\\Q:\\E"));
			}

			else
			{
				o.add(i);
			}

			String pf = o.get(0);
			o.remove(0);
			getSequences().get(i).resetExtremes();
			double vmin = Math.abs(getSequences().get(i).getMin());
			double vmed = Math.abs(getSequences().get(i).getMedian());
			double vavg = Math.abs(getSequences().get(i).getAverage());
			double vmax = Math.abs(getSequences().get(i).getMax());

			for(String k : o)
			{
				if(k.startsWith("x"))
				{
					Double mult = Double.valueOf(k.substring(1));
					vmin *= mult / (scale * 2D);
					vmed *= mult / (scale * 2D);
					vavg *= mult / (scale * 2D);
					vmax *= mult / (scale * 2D);
				}
			}

			boolean ms = false;
			boolean comma = false;
			String myparent = null;
			int dot = 0;
			for(String k : o)
			{
				if(k.startsWith("/"))
				{
					myparent = k.substring(1);
				}

				if(k.startsWith(".") && k.endsWith("."))
				{
					dot = k.length();
				}

				else if(k.equals(","))
				{
					comma = true;
				}

				if(k.equals("ms"))
				{
					ms = true;
				}
			}

			if((parent != null) != (myparent != null))
			{
				continue looking;
			}

			if(parent != null && !myparent.equals(parent))
			{
				continue looking;
			}

			if(dot == 0 && vavg >= 1000 && !comma)
			{
				comma = true;
			}

			String af = ms ? F.duration(vmin, dot) : comma ? F.f((int) vmin) : F.f(vmin, dot);
			String bf = ms ? F.duration(vmed, dot) : comma ? F.f((int) vmed) : F.f(vmed, dot);
			String cf = ms ? F.duration(vavg, dot) : comma ? F.f((int) vavg) : F.f(vavg, dot);
			String df = ms ? F.duration(vmax, dot) : comma ? F.f((int) vmax) : F.f(vmax, dot);

			out.put(pf, C.DARK_GREEN.toString() + C.ITALIC + cf + C.RESET + C.GRAY + " (" + C.DARK_AQUA + C.ITALIC + af + C.RESET + C.GRAY + " > " + C.GOLD + C.ITALIC + bf + C.RESET + C.GRAY + " < " + C.DARK_RED + C.ITALIC + df + C.RESET + C.GRAY + ")");
		}

		if(ind == 0)
		{
			c.accept(C.WHITE.toString() + C.BOLD + "Total Generators: " + C.RESET + C.DARK_AQUA + C.ITALIC + F.f(generators));
			c.accept(C.WHITE.toString() + C.BOLD + "Parallelism: " + C.RESET + C.DARK_PURPLE + C.ITALIC + F.pc(scale) + C.WHITE + C.BOLD + " Threads: " + C.RESET + C.BLUE + C.ITALIC + Iris.getController(ExecutionController.class).getTC());
		}

		for(String i : out.k())
		{
			String g = F.capitalizeWords(i.replaceAll("\\Q-\\E", " ").toLowerCase());
			c.accept(F.repeat("  ", M.iclip(ind, 0, 4)) + C.WHITE + C.BOLD + g + C.RESET + ": " + out.get(i));

			send(p, c, i, ind + 1);
		}
	}
}
