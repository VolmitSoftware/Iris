package com.volmit.iris.util;

import com.volmit.iris.Iris;

public abstract class Looper extends Thread
{
	public void run()
	{
		while(!interrupted())
		{
			try
			{
				long m = loop();

				if(m < 0)
				{
					break;
				}

				Thread.sleep(m);
			}

			catch(InterruptedException e)
			{
				break;
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}

		Iris.info("Thread " + getName() + " Shutdown.");
	}

	protected abstract long loop();
}
