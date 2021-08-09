package com.volmit.plague.api.command;

import java.lang.reflect.Parameter;

import com.volmit.plague.api.Description;
import com.volmit.plague.api.Name;
import com.volmit.plague.api.Optional;
import com.volmit.plague.util.C;
import com.volmit.plague.util.ColoredString;
import com.volmit.plague.util.RTEX;
import com.volmit.plague.util.RTX;

import lombok.Data;

@Data
public class PlagueCommandParameter
{
	private final String description;
	private final boolean required;
	private final Class<?> type;
	private final Optional optional;
	private final boolean vararg;
	private final String name;

	public PlagueCommandParameter(Parameter p)
	{
		this.name = p.isAnnotationPresent(Name.class) ? p.getDeclaredAnnotation(Name.class).value() : p.getName();
		this.description = p.isAnnotationPresent(Name.class) ? p.getDeclaredAnnotation(Description.class).value() : "No Description";
		this.required = !p.isAnnotationPresent(Optional.class);
		optional = p.getDeclaredAnnotation(Optional.class);
		this.type = p.getType();
		vararg = p.isVarArgs();
	}

	public RTX getHelp()
	{
		RTX rtx = new RTX();
		String open = required ? "<" : "[";
		String close = required ? ">" : "]";
		//@builder
		RTEX rtex = new RTEX(
				new ColoredString(C.GRAY, open), 
				new ColoredString(C.WHITE, name), 
				new ColoredString(C.GRAY, close),
				new ColoredString(C.GRAY, "\n" + getDescription()),
				new ColoredString(required ? C.RED : C.AQUA, "\n" + (required ? "REQUIRED" : "Optional")),
				new ColoredString(C.GREEN, " " + (type.getSimpleName())),
				new ColoredString(C.GRAY, required ? "" : (" (" + fitDefault().toString() + ")"))
				);
		//@done
		rtx.addText(open, C.GRAY);
		rtx.addTextHover(name, rtex, C.WHITE);
		rtx.addText(close + " ", C.GRAY);
		return rtx;
	}

	public Object fitDefault()
	{
		if(getType().isArray())
		{
			return new Object[0];
		}

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
}
