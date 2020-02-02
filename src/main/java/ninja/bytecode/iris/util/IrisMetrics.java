package ninja.bytecode.iris.util;

import java.util.function.Consumer;

import org.bukkit.command.CommandSender;

import mortar.compute.math.M;
import mortar.util.text.C;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.format.Form;
import ninja.bytecode.shuriken.math.RollingSequence;

public class IrisMetrics
{
	private int size;
	private int generators;
	private double scale;
	private KMap<String, RollingSequence> sequences;

	public IrisMetrics(int generators, int size)
	{
		scale = 1;
		this.size = size;
		this.generators = generators;
		sequences = new KMap<>();
	}

	public String avgMS(String s, int dec)
	{
		return Form.duration(get(s).getAverage(), dec);
	}

	public String avg(String s, int dec)
	{
		return Form.f(get(s).getAverage(), dec);
	}

	public String maxMS(String s, int dec)
	{
		return Form.duration(get(s).getMax(), dec);
	}

	public String max(String s, int dec)
	{
		return Form.f(get(s).getMax(), dec);
	}

	public String minMS(String s, int dec)
	{
		return Form.duration(get(s).getMin(), dec);
	}

	public String min(String s, int dec)
	{
		return Form.f(get(s).getMin(), dec);
	}

	public String medianMS(String s, int dec)
	{
		return Form.duration(get(s).getMedian(), dec);
	}

	public String median(String s, int dec)
	{
		return Form.f(get(s).getMedian(), dec);
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

	public KMap<String, RollingSequence> getSequences()
	{
		return sequences;
	}

	public void setSequences(KMap<String, RollingSequence> sequences)
	{
		this.sequences = sequences;
	}

	public void send(CommandSender p, Consumer<String> c)
	{
		send(p, c, null, 0);
	}

	public void setParScale(double sf)
	{
		scale = sf;
	}

	public void send(CommandSender p, Consumer<String> c, String parent, int ind)
	{
		KMap<String, String> out = new KMap<>();

		looking: for(String i : getSequences().k())
		{
			KList<String> o = new KList<>();

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

			String af = ms ? Form.duration(vmin, dot) : comma ? Form.f((int) vmin) : Form.f(vmin, dot);
			String bf = ms ? Form.duration(vmed, dot) : comma ? Form.f((int) vmed) : Form.f(vmed, dot);
			String cf = ms ? Form.duration(vavg, dot) : comma ? Form.f((int) vavg) : Form.f(vavg, dot);
			String df = ms ? Form.duration(vmax, dot) : comma ? Form.f((int) vmax) : Form.f(vmax, dot);

			out.put(pf, C.DARK_GREEN.toString() + C.ITALIC + cf + C.RESET + C.GRAY + " (" + C.DARK_AQUA + C.ITALIC + af + C.RESET + C.GRAY + " > " + C.GOLD + C.ITALIC + bf + C.RESET + C.GRAY + " < " + C.DARK_RED + C.ITALIC + df + C.RESET + C.GRAY + ")");
		}

		if(ind == 0)
		{
			c.accept(C.WHITE.toString() + C.BOLD + "Total Generators: " + C.RESET + C.DARK_AQUA + C.ITALIC + Form.f(generators));
			c.accept(C.WHITE.toString() + C.BOLD + "Parallelism: " + C.RESET + C.DARK_PURPLE + C.ITALIC + Form.pc(scale) + C.WHITE + C.BOLD + " Threads: " + C.RESET + C.BLUE + C.ITALIC + Iris.exec().getTC());
		}

		for(String i : out.k())
		{
			String g = Form.capitalizeWords(i.replaceAll("\\Q-\\E", " ").toLowerCase());
			c.accept(Form.repeat("  ", M.iclip(ind, 0, 4)) + C.WHITE + C.BOLD + g + C.RESET + ": " + out.get(i));

			send(p, c, i, ind + 1);
		}
	}
}
