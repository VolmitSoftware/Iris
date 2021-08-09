package com.volmit.plague.api.command;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.plague.util.PlagueTypeException;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.plague.api.PlagueSender;
import com.volmit.iris.util.format.ColoredString;
import com.volmit.plague.util.RTEX;
import com.volmit.plague.util.RTX;

import lombok.Data;


@Data
public class PlagueCommandFunction
{
	private static final KList<String> TRUE = new KList<>("1", "true", "t", "yes", "y", "+");
	private static final KList<String> FALSE = new KList<>("0", "false", "f", "no", "n", "-");

	private final Method method;
	private final KList<PlagueCommandParameter> parameters;
	private int maxParameters;
	private int minParameters;
	private PlagueCommand command;

	public PlagueCommandFunction(Method method, PlagueCommand command)
	{
		this.method = method;
		this.command = command;
		this.parameters = new KList<>();
		int optional = 0;
		int required = 0;
		boolean infiniteOptional = false;
		boolean infiniteRequired = false;

		for(Parameter i : method.getParameters())
		{
			if(i.getType().equals(PlagueSender.class))
			{
				continue;
			}

			PlagueCommandParameter pp = new PlagueCommandParameter(i);
			parameters.add(pp);

			if(pp.isRequired())
			{
				if(pp.isVararg())
				{
					infiniteRequired = true;
				}

				else
				{
					required++;
				}
			}

			else
			{
				if(pp.isVararg())
				{
					infiniteOptional = true;
				}

				else
				{
					optional++;
				}
			}
		}

		maxParameters = infiniteOptional || infiniteRequired ? -1 : (required + optional);
		minParameters = required;
	}

	public RTX getHelp()
	{
		RTX rtx = new RTX();

		RTEX rtex = new RTEX(new ColoredString(C.WHITE, command.getNode()), new ColoredString(C.GRAY, "\n" + command.getDescription()));
		rtx.addTextHover(command.getNode() + " ", rtex, C.LIGHT_PURPLE);

		for(PlagueCommandParameter i : parameters)
		{
			rtx.addAll(i.getHelp());
		}

		return rtx;
	}

	public String invoke(PlagueCommand instance, PlagueSender sender, KList<String> s)
	{
		KList<PlagueCommandParameter> fitter = parameters.copy();
		KList<String> forcer = s.copy();
		KList<Object> fitted = new KList<>();
		KList<Integer> skip = new KList<>();

		trimming: while(forcer.size() < fitter.size())
		{
			for(PlagueCommandParameter i : fitter.copy().reverse())
			{
				if(i.isVararg())
				{
					continue;
				}

				if(!i.isRequired())
				{
					skip.add(fitter.indexOf(i));
					fitter.remove(i);
					continue trimming;
				}
			}

			return "Not enough arguments (min = " + minParameters + ")";
		}

		for(int i = 0; i < parameters.size(); i++)
		{
			PlagueCommandParameter par = parameters.get(i);
			boolean last = parameters.size() - 1 == i;

			if(skip.contains(i))
			{
				fitted.add(par.fitDefault());
				continue;
			}

			if(last)
			{
				if(par.isVararg())
				{
					KList<Object> objects = new KList<>();
					int pb = 0;
					for(String j : s)
					{
						try
						{
							objects.add(par.getType().getComponentType(), j);
						}

						catch(Throwable e)
						{
							return "Failed to fit var-parameter #" + (i + 1) + "[" + pb + "] " + " -> \"" + j + "\" (" + par.getType().getComponentType().getSimpleName() + "[" + pb + "]): " + e.getMessage();
						}

						pb++;
					}

					fitted.add(objects.toArray(new Object[0]));
					continue;
				}
			}

			String v = s.isEmpty() ? par.fitDefault().toString() : s.pop();

			try
			{
				fitted.add(convert(v, par.getType()));
			}

			catch(Throwable e)
			{
				return "Failed to fit parameter #" + (i + 1) + " -> \"" + v + "\" (" + par.getType().getSimpleName() + "): " + e.getMessage();
			}
		}

		try
		{
			fitted.add(0, sender);
			method.invoke(instance, fitted.toArray(new Object[0]));
		}

		catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
		{
			e.printStackTrace();
			return "Failed to invoke: " + e.getClass().getSimpleName() + ": " + e.getMessage();
		}

		return null;
	}

	/**
	 * Convert the String object to the specified type
	 * @param object The String object to convert
	 * @param type
	 * @return
	 * @throws PlagueTypeException
	 */
	public Object convert(String object, Class<?> type) throws PlagueTypeException {

		if(type.equals(int.class) || type.equals(Integer.class))
		{
			try {
				return Integer.valueOf(object);
			} catch (NumberFormatException ignored){
				throw new PlagueTypeException("Attempted cast from " + object + " to integer failed (check the argument)");
			}
		}

		if(type.equals(float.class) || type.equals(Float.class))
		{
			try {
				return Float.valueOf(object);
			} catch (NumberFormatException ignored){
				throw new PlagueTypeException("Attempted cast from " + object + " to float failed (check the argument)");
			}
		}

		if(type.equals(double.class) || type.equals(Double.class))
		{
			try {
				return Double.valueOf(object);
			} catch (NumberFormatException ignored){
				throw new PlagueTypeException("Attempted cast from " + object + " to double failed (check the argument)");
			}
		}

		if(type.equals(long.class) || type.equals(Long.class))
		{
			try {
				return Long.valueOf(object);
			} catch (NumberFormatException ignored){
				throw new PlagueTypeException("Attempted cast from " + object + " to long failed (check the argument)");
			}
		}

		if(type.equals(String.class))
		{
			return object;
		}

		if(type.equals(Player.class))
		{
			// TODO: Support remote players
			Player p = Bukkit.getPlayer(object);

			if(p == null)
			{
				throw new PlagueTypeException("Cannot find player '" + object + "'");
			}

			return p;
		}

		if(type.equals(World.class))
		{
			World w = Bukkit.getWorld(object);

			if(w == null)
			{
				throw new PlagueTypeException("Cannot find world '" + object + "'");
			}

			return w;
		}

		if(type.equals(boolean.class))
		{
			if (TRUE.contains(object)){
				return true;
			}
			if (FALSE.contains(object)){
				return false;
			}
			throw new PlagueTypeException("Cannot find TRUE or FALSE value for: " + object + " (not in definitions lists)");
		}

		throw new PlagueTypeException("Plague does not support the parameter type " + type.getCanonicalName());
	}

	/**
	 * Retrieve whether this function is valid with the provided
	 * @param amount of arguments
	 * @return true if the amount of arguments is supported
	 */
	public boolean isArgumentAmountSupported(int amount)
	{
		return amount >= minParameters && amount <= maxParameters;
	}
}
