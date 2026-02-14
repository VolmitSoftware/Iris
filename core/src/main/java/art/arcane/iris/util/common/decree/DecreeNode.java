package art.arcane.iris.util.decree;

import art.arcane.volmlib.util.decree.DecreeNodeBase;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class DecreeNode extends DecreeNodeBase<DecreeParameter> {
    public DecreeNode(Object instance, Method method) {
        super(instance, method);
    }

    @Override
    protected DecreeParameter createParameter(Parameter parameter) {
        return new DecreeParameter(parameter);
    }
}
