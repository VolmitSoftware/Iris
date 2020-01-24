package ninja.bytecode.iris.generator.genobject;

public class PhantomGenObject
{
	private GenObject object;
	private String name;

	public PhantomGenObject(GenObject object)
	{
		this.object = object;
		this.name = object.getName();
	}
}
