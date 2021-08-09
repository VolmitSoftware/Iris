package com.volmit.plague.api.command;

import java.lang.reflect.Parameter;

import com.volmit.iris.util.format.C;
import com.volmit.plague.api.annotations.Description;
import com.volmit.plague.api.annotations.Name;
import com.volmit.plague.api.annotations.Optional;
import com.volmit.iris.util.format.ColoredString;
import com.volmit.plague.util.RTEX;
import com.volmit.plague.util.RTX;

import lombok.Data;

@Data
public class PlagueCommandParameter
{
	private final String name;
	private final String description;
	private final Class<?> type;
	private final Optional optional;
	private final boolean vararg;

	public PlagueCommandParameter(Parameter p)
	{
		name = p.isAnnotationPresent(Name.class) ? p.getDeclaredAnnotation(Name.class).value() : p.getName();
		description = p.isAnnotationPresent(Name.class) ? p.getDeclaredAnnotation(Description.class).value() : "No Description";
		optional = p.getAnnotation(Optional.class);
		type = p.getType();
		vararg = p.isVarArgs();
	}

	/**
	 * @return the help description for this parameter (hoverable text)
	 */
	public RTX getHelp()
	{
		RTEX rtex = new RTEX()
				.add(C.GRAY, isRequired() ? "<" : "[")
				.add(C.WHITE, name)
				.add(C.GRAY, isRequired() ? ">" : "]")
				.add(C.GRAY, "\n" + getDescription())
				.add(isRequired() ? C.RED : C.AQUA, "\n" + (isRequired() ? "REQUIRED" : "Optional"))
				.add(C.GREEN, " " + (type.getSimpleName()))
				.add(C.GRAY, isRequired() ? "" : (" (" + fitDefault().toString() + ")"));

		return new RTX()
				.addText(isRequired() ? "<" : "[", C.GRAY)
				.addTextHover(name, rtex, C.WHITE)
				.addText(isRequired() ? ">" : "]" + " ", C.GRAY);
	}

	/**
	 * Fit an object to its default
	 * @return the default type;
	 */
	public Object fitDefault()
	{
		if(getType().equals(String.class))
		{
			return getOptional().defaultString();
		}

		if(getType().equals(int.class))
		{
			return getOptional().defaultInt();
		}

		if(getType().equals(float.class))
		{
			return getOptional().defaultFloat();
		}

		if(getType().equals(double.class))
		{
			return getOptional().defaultDouble();
		}

		if(getType().equals(long.class))
		{
			return getOptional().defaultLong();
		}

		if(getType().equals(boolean.class))
		{
			return getOptional().defaultBoolean();
		}

		if(getType().equals(Integer.class))
		{
			return getOptional().defaultInt();
		}

		if(getType().equals(Float.class))
		{
			return getOptional().defaultFloat();
		}

		if(getType().equals(Double.class))
		{
			return getOptional().defaultDouble();
		}

		if(getType().equals(Long.class))
		{
			return getOptional().defaultLong();
		}

		if(getType().equals(Boolean.class))
		{
			return getOptional().defaultBoolean();
		}

		return "null";
	}

	/**
	 * @return true if the parameter is required
	 */
	public boolean isRequired() {
		return optional == null;
	}
}
