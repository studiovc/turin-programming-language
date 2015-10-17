package me.tomassetti.turin.definitions;

import me.tomassetti.jvm.JvmConstructorDefinition;
import me.tomassetti.turin.symbols.FormalParameter;

import java.util.List;

public class InternalConstructorDefinition extends InternalInvokableDefinition {

    private JvmConstructorDefinition jvmConstructorDefinition;

    public JvmConstructorDefinition getJvmConstructorDefinition() {
        return jvmConstructorDefinition;
    }

    public InternalConstructorDefinition(List<? extends FormalParameter> formalParameters, JvmConstructorDefinition jvmConstructorDefinition) {
        super(formalParameters);
        this.jvmConstructorDefinition = jvmConstructorDefinition;
    }

}