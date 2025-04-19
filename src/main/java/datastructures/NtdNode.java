package datastructures;

import java.util.List;
import java.util.Set;

public class NtdNode {

    NtdNode firstChild;
    NtdNode secondChild; 

    Set<Integer> bag;

    Integer specialVertex; 

    NodeType nodeType;

    Set<Integer> forgottenVertices; 
    List<Integer> firstChildIntroducedVertices; 
    List<Integer> secondChildIntroducedVertices; 

    public final int id;

    public NtdNode(int id) {
        this.id = id;
    }



    @Override
    public String toString() { 
        StringBuilder sb = new StringBuilder();
        for (int v : bag) {
            sb.append(v).append(" ");
        }
        sb.append(nodeType);
        if(specialVertex != null) sb.append(" ").append(specialVertex);
        if(forgottenVertices != null) {
            sb.append(" fj ");
            for (int v : forgottenVertices) {
                sb.append(v).append(" ");
            }
            if(firstChildIntroducedVertices != null) {
                sb.append(" first_ij ");
                sb.append(" ");
                for (int v : firstChildIntroducedVertices) {
                    sb.append(v).append(" ");
                }
            }
            if(secondChildIntroducedVertices != null) {
                sb.append(" second_ij ");
                sb.append(" ");
                for (int v : secondChildIntroducedVertices) {
                    sb.append(v).append(" ");
                }
            }


        }
        return sb.toString();
    }

    public NtdNode getFirstChild() {
        return firstChild;
    }

    public NtdNode getSecondChild() {
        return secondChild;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public Set<Integer> getBag() {
        return bag;
    }

    public Integer getSpecialVertex() {
        return specialVertex;
    }

    public Set<Integer> getForgottenVertices() {
        return forgottenVertices;
    }

    public List<Integer> getFirstChildIntroducedVertices() {
        return firstChildIntroducedVertices;
    }

    public List<Integer> getSecondChildIntroducedVertices() {
        return secondChildIntroducedVertices;
    }

    public enum NodeType {
        LEAF,
        INTRODUCE,
        FORGET,
        JOIN,
        JOIN_FORGET,
        INTRODUCE_JOIN_FORGET
    }
}
