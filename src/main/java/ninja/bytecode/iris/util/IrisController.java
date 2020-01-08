package ninja.bytecode.iris.util;

import org.bukkit.event.Listener;

public interface IrisController extends Listener
{
	public void onStart();
	
	public void onStop();
}
