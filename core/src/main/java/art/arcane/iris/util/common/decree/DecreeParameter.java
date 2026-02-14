package art.arcane.iris.util.decree;

import art.arcane.volmlib.util.decree.DecreeParameterBase;
import art.arcane.volmlib.util.decree.specialhandlers.NoParameterHandler;
import art.arcane.iris.util.decree.specialhandlers.DummyHandler;

import java.lang.reflect.Parameter;

public class DecreeParameter extends DecreeParameterBase {
    public DecreeParameter(Parameter parameter) {
        super(parameter);
    }

    @Override
    protected boolean useSystemHandler(Class<?> customHandler) {
        return customHandler.equals(NoParameterHandler.class) || customHandler.equals(DummyHandler.class);
    }

    @Override
    protected art.arcane.volmlib.util.decree.DecreeParameterHandler<?> getSystemHandler(Class<?> type) {
        return DecreeSystem.getHandler(type);
    }

    @Override
    public DecreeParameterHandler<?> getHandler() {
        return (DecreeParameterHandler<?>) super.getHandler();
    }
}
