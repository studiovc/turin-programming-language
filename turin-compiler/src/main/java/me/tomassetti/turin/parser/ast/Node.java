package me.tomassetti.turin.parser.ast;

public abstract class Node {

    protected Node parent;
    private Position position;

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public Node getParent() {
        return parent;
    }

    public abstract Iterable<Node> getChildren();

    public void setParent(Node parent){
        this.parent = parent;
    }

    public String contextName() {
        if (parent == null) {
            return "";
        }
        if (parent instanceof TurinFile) {
            TurinFile turinFile = (TurinFile)parent;
            return turinFile.getNamespaceDefinition().getName();
        }
        return parent.contextName();
    }
}
