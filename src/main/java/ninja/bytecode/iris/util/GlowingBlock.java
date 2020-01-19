package ninja.bytecode.iris.util;

import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import mortar.api.nms.NMP;
import mortar.api.world.MaterialBlock;
import mortar.compute.math.M;
import mortar.util.text.C;

public class GlowingBlock
{
	private static int idd = 123456789;
	private int id;
	private UUID uid;
	private Location location;
	private Location current;
	private Player player;
	private double factor;
	private Vector velocity;
	private boolean active;
	private long mv = M.ms();
	private MaterialBlock mb;
	private ChatColor c;

	public GlowingBlock(Player player, Location init, MaterialBlock mb, ChatColor c)
	{
		this.mb = mb;
		this.uid = UUID.randomUUID();
		this.id = idd--;
		location = init;
		current = init.clone();
		this.player = player;
		factor = Math.PI;
		active = false;
		velocity = new Vector();
		this.c = c;
	}

	public int getId()
	{
		return id;
	}

	public void sendMetadata(boolean glowing)
	{
		PacketGate.mark(PacketCategory.EFFECT);
		NMP.host.sendEntityMetadata(player, id, NMP.host.getMetaEntityProperties(false, false, false, false, false, glowing, false));
	}

	public void sendMetadata(ChatColor c)
	{
		PacketGate.mark(PacketCategory.EFFECT);
		//NMP.host.sendGlowingColorMetaEntity(getPlayer(), uid, C.values()[c.ordinal()]);
	}

	public void update()
	{
		if(M.ms() - mv < 50)
		{
			return;
		}

		if(location.getX() == current.getX() && location.getY() == current.getY() && location.getZ() == current.getZ())
		{
			return;
		}

		mv = M.ms();

		if(location.distanceSquared(current) > 16)
		{
			if(PacketGate.can(PacketCategory.EFFECT))
			{
				sendTeleport(location);
				current = location;
			}
		}

		else
		{
			if(PacketGate.can(PacketCategory.EFFECT))
			{
				double dx = location.getX() - current.getX();
				double dy = location.getY() - current.getY();
				double dz = location.getZ() - current.getZ();
				dx += velocity.getX();
				dy += velocity.getY();
				dz += velocity.getZ();
				dx = M.clip(dx, -8, 8);
				dy = M.clip(dy, -8, 8);
				dz = M.clip(dz, -8, 8);
				sendMove(dx / factor, dy / factor, dz / factor);
				current.add(dx / factor, dy / factor, dz / factor);
				current.setX(Math.abs(location.getX() - current.getX()) < 0.00001 ? location.getX() : current.getX());
				current.setY(Math.abs(location.getY() - current.getY()) < 0.00001 ? location.getY() : current.getY());
				current.setZ(Math.abs(location.getZ() - current.getZ()) < 0.00001 ? location.getZ() : current.getZ());

				if(location.getX() == current.getX() && location.getY() == current.getY() && location.getZ() == current.getZ())
				{
					if(PacketGate.can(PacketCategory.EFFECT))
					{
						sendTeleport(location);
						current = location;
					}
				}
			}
		}
	}

	public Location getPosition()
	{
		return location.clone();
	}

	public void setPosition(Location l)
	{
		location = l;
	}

	public Player getPlayer()
	{
		return player;
	}

	public void destroy()
	{
		sendDestroy();
	}

	public void create()
	{
		sendSpawn();
	}

	public boolean isActive()
	{
		return active;
	}

	public void setFactor(int i)
	{
		factor = i;
	}

	private void sendTeleport(Location l)
	{
		NMP.host.teleportEntity(id, player, l, false);
	}

	private void sendMove(double x, double y, double z)
	{
		NMP.host.moveEntityRelative(id, player, x, y, z, false);
	}

	public void sendDestroy()
	{
		active = false;
		NMP.host.removeEntity(id, player);
		NMP.host.sendRemoveGlowingColorMetaEntity(getPlayer(), uid);
		sendMetadata(false);
		PacketGate.mark(PacketCategory.EFFECT);
		PacketGate.mark(PacketCategory.EFFECT);
	}

	public void sendSpawn()
	{
		NMP.host.spawnFallingBlock(id, uid, location, player, mb);
		sendMetadata(c);
		sendMetadata(true);
		active = true;
		PacketGate.mark(PacketCategory.EFFECT);
		PacketGate.mark(PacketCategory.EFFECT);
	}
}
