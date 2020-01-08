package ninja.bytecode.iris.controller;

import ninja.bytecode.iris.util.IrisController;
import ninja.bytecode.shuriken.bench.Profiler;

public class TimingsController implements IrisController
{
	public Profiler profiler;

	@Override
	public void onStart()
	{
		profiler = new Profiler(768);
	}

	@Override
	public void onStop()
	{

	}

	public void started(String obj)
	{
		profiler.start(obj);
	}

	public void stopped(String obj)
	{
		profiler.stop(obj);
	}

	public double getResult(String tag)
	{
		return profiler.getResult(tag).getAverage();
	}
}
