package ninja.bytecode.iris.objectproperty;

import java.lang.reflect.Field;

import lombok.Data;

@Data
public class ObjectProperty<T>
{
	private Class<? extends T> type;
	private String fieldName;
	private String name;
	private String description;
	private Object instance;
	private Field field;

	public ObjectProperty(Class<? extends T> type, String fieldName) throws Throwable
	{
		this.type = type;
		this.fieldName = fieldName;
		field = type.getDeclaredField(name);
		field.setAccessible(true);

		if(field.isAnnotationPresent(Property.class))
		{
			Property p = field.getAnnotation(Property.class);
			name = p.name();
			description = p.description();
		}
	}

	public void set(T value) throws Throwable
	{
		field.set(instance, value);
	}

	@SuppressWarnings("unchecked")
	public T get() throws Throwable
	{
		return (T) field.get(instance);
	}
}
