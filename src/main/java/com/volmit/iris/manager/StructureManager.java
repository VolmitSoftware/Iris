package com.volmit.iris.manager;

import org.bukkit.entity.Player;

import com.volmit.iris.manager.structure.StructureTemplate;
import com.volmit.iris.util.KList;

public class StructureManager
{
	private KList<StructureTemplate> openEditors;

	public StructureManager()
	{
		this.openEditors = new KList<>();
	}

	public void closeall()
	{
		for(StructureTemplate i : openEditors.copy())
		{
			i.close();
		}
	}

	public void open(StructureTemplate t)
	{
		for(StructureTemplate i : openEditors.copy())
		{
			if(t.getWorker().equals(i.getWorker()))
			{
				i.close();
			}
		}

		openEditors.add(t);
	}

	public void remove(StructureTemplate s)
	{
		openEditors.remove(s);
	}

	public StructureTemplate get(Player p)
	{
		for(StructureTemplate i : openEditors)
		{
			if(i.getWorker().equals(p))
			{
				return i;
			}
		}

		return null;
	}
}
