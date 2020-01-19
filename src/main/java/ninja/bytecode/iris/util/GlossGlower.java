package ninja.bytecode.iris.util;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import mortar.api.nms.NMP;
import mortar.util.text.C;

public class GlossGlower implements Glower
{
	private final Player observer;
	private final Entity entity;
	private ChatColor color;
	private boolean sentTeam;

	public GlossGlower(Entity entity, Player observer)
	{
		sentTeam = false;
		this.entity = entity;
		this.observer = observer;
		this.color = ChatColor.WHITE;
	}

	@Override
	public Entity getEntity()
	{
		return entity;
	}

	@Override
	public ChatColor getColor()
	{
		return color;
	}

	@Override
	public void setColor(ChatColor color)
	{
		if(color.isFormat())
		{
			throw new UnsupportedOperationException("You cannot use format codes for glow colors");
		}

		this.color = color;

		if(observer == null)
		{
			for(Player i : entity.getWorld().getPlayers())
			{
				NMP.host.sendGlowingColorMeta(i, getEntity(), C.values()[color.ordinal()]);
			}
		}

		else
		{
			NMP.host.sendGlowingColorMeta(getObserver(), getEntity(), C.values()[color.ordinal()]);
		}
	}

	@Override
	public void setGlowing(boolean glowing)
	{
		if(observer == null)
		{
			for(Player i : entity.getWorld().getPlayers())
			{
				NMP.host.sendEntityMetadata(i, getEntity().getEntityId(), NMP.host.getMetaEntityProperties(false, false, false, false, false, glowing, false));
			}
		}

		else
		{
			NMP.host.sendEntityMetadata(observer, getEntity().getEntityId(), NMP.host.getMetaEntityProperties(false, false, false, false, false, glowing, false));
		}
	}

	@Override
	public void destroy()
	{
		setGlowing(false);

		if(sentTeam)
		{
			if(observer == null)
			{
				for(Player i : entity.getWorld().getPlayers())
				{
					NMP.host.sendRemoveGlowingColorMeta(i, getEntity());
				}
			}

			else
			{
				NMP.host.sendRemoveGlowingColorMeta(getObserver(), getEntity());
			}

		}
	}

	@Override
	public Player getObserver()
	{
		return observer;
	}
}
