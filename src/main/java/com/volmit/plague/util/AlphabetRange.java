package com.volmit.plague.util;

public class AlphabetRange
{
	private Alphabet from;
	private Alphabet to;

	public AlphabetRange(Alphabet from, Alphabet to)
	{
		this.from = from;
		this.to = to;
	}

	public Alphabet getFrom()
	{
		return from;
	}

	public void setFrom(Alphabet from)
	{
		this.from = from;
	}

	public Alphabet getTo()
	{
		return to;
	}

	public void setTo(Alphabet to)
	{
		this.to = to;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((from == null) ? 0 : from.hashCode());
		result = prime * result + ((to == null) ? 0 : to.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
		{
			return true;
		}
		if(obj == null)
		{
			return false;
		}
		if(!(obj instanceof AlphabetRange))
		{
			return false;
		}
		AlphabetRange other = (AlphabetRange) obj;
		if(from != other.from)
		{
			return false;
		}
		if(to != other.to)
		{
			return false;
		}
		return true;
	}
}
