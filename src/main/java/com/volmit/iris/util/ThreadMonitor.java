package com.volmit.iris.util;

import com.volmit.iris.Iris;

/**
 * Not particularly efficient or perfectly accurate but is great at fast thread
 * switching detection
 * 
 * @author dan
 *
 */
public class ThreadMonitor extends Thread
{
	private Thread monitor;
	private boolean running;
	private State lastState;
	private ChronoLatch cl;
	private PrecisionStopwatch st;
	int cycles = 0;
	private RollingSequence sq = new RollingSequence(3);

	private ThreadMonitor(Thread monitor)
	{
		running = true;
		st = PrecisionStopwatch.start();
		this.monitor = monitor;
		lastState = State.NEW;
		cl = new ChronoLatch(1000);
		start();
	}

	public void run()
	{
		while(running)
		{
			try
			{
				Thread.sleep(0);
				State s = monitor.getState();
				if(lastState != s)
				{
					cycles++;
					pushState(s);
				}

				lastState = s;

				if(cl.flip())
				{
					Iris.info("Cycles: " + Form.f(cycles) + " (" + Form.duration(sq.getAverage(), 2) + ")");
				}
			}

			catch(Throwable e)
			{
				running = false;
				break;
			}
		}
	}

	public void pushState(State s)
	{
		if(s != State.RUNNABLE)
		{
			if(st != null)
			{
				sq.put(st.getMilliseconds());
			}
		}

		else
		{

			st = PrecisionStopwatch.start();
		}
	}

	public void unbind()
	{
		running = false;
	}

	public static ThreadMonitor bind(Thread monitor)
	{
		return new ThreadMonitor(monitor);
	}
}
