package me.tomassetti.turin.parser.ast.expressions;

import com.google.common.collect.ImmutableList;
import me.tomassetti.turin.parser.ast.Node;
import me.tomassetti.turin.typesystem.PrimitiveTypeUsage;
import me.tomassetti.turin.typesystem.TypeUsage;

public class NotOperation extends Expression {
    private Expression value;

    @Override
    public Iterable<Node> getChildren() {
        return ImmutableList.of(value);
    }

    @Override
    public TypeUsage calcType() {
        return PrimitiveTypeUsage.BOOLEAN;
    }

    public Expression getValue() {
        return value;
    }

    public NotOperation(Expression value) {
        this.value = value;
        this.value.setParent(this);
    }
}
