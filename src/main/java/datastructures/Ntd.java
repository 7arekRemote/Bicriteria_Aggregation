package datastructures;

import java.util.*;

import static datastructures.NtdNode.NodeType.JOIN;

public class Ntd implements Iterable<NtdNode>{

    public NtdNode root;

    public Map<Integer, NtdNode> nodeMap;

    public Ntd() {
    }

    int tw;
    int numberOfNodes;
    int numberOfJoinNodes;




    Map<NtdNode, HashMap<Integer, Integer>> pathIndicesMap;
    Map<NtdNode, Integer> pathMaxBagSize;

    public void computePathIndices() {
        pathIndicesMap = new HashMap<>();
        pathMaxBagSize = new HashMap<>();
        computePathIndices(root);
    }

    private void computePathIndices(NtdNode node) {
        
        
        HashMap<Integer, Integer> pathIndex = new HashMap<>();
        Stack<Integer> indices = new Stack<>();
        int i = getTw()+1; while (i --> 0) indices.push(i); 
        
        ArrayList<Integer> sortedBag = new ArrayList<>(node.getBag());
        sortedBag.sort(Integer::compare);
        for (Integer vertex : sortedBag) {
            pathIndex.put(vertex, indices.pop());
        }

        
        
        int maxBagSize = 0;
        while (true) {
            maxBagSize = Math.max(maxBagSize, node.getBag().size());
            switch (node.getNodeType()) {
                case INTRODUCE -> {
                    pathIndicesMap.put(node, pathIndex); 
                    indices.push(pathIndex.get(node.getSpecialVertex()));
                }
                case FORGET -> {
                    pathIndicesMap.put(node, pathIndex); 
                    pathIndex.put(node.getSpecialVertex(), indices.pop());
                }
                case JOIN, LEAF -> {
                    
                    
                    pathIndicesMap.put(node, pathIndex);
                    pathMaxBagSize.put(node, maxBagSize);

                    
                    if (node.getNodeType() == JOIN) {
                        computePathIndices(node.getFirstChild());
                        computePathIndices(node.getSecondChild());
                    }
                    
                    return;
                }
            }
            node = node.getFirstChild();
        }
    }

    @Override
    public Iterator<NtdNode> iterator() {
        return new PostOrderIterator(root);
    }

    private static class PostOrderIterator implements Iterator<NtdNode> {
        private final Stack<NtdNode> stack = new Stack<>();
        private final Set<NtdNode> visited = new HashSet<>();
        private NtdNode nextNode;

        public PostOrderIterator(NtdNode root) {
            if (root != null) {
                stack.push(root);
                advance();
            }
        }

        private void advance() {
            nextNode = null;
            while (!stack.isEmpty() && nextNode == null) {
                NtdNode node = stack.peek();
                if (visited.contains(node)) {
                    stack.pop();
                    nextNode = node;
                } else {
                    visited.add(node);
                    if (node.getFirstChild() != null) stack.push(node.getFirstChild());
                    if (node.getSecondChild() != null) stack.push(node.getSecondChild());
                }
            }
        }

        @Override
        public boolean hasNext() {
            return nextNode != null;
        }

        @Override
        public NtdNode next() {
            if (!hasNext()) throw new NoSuchElementException();
            NtdNode current = nextNode;
            advance();
            return current;
        }
    }

    public void createNodeIdMap(){
        nodeMap = new HashMap<>();
        
        for (NtdNode ntdNode : this) {
            nodeMap.put(ntdNode.id, ntdNode);
        }
    }


    public String toGraphviz() {
        StringBuilder sb = new StringBuilder();

        
        sb.append("graph {\n" +
                "\t\tnode [shape=circle,style=filled,color=black, fontsize=130, penwidth=10];\n" +
                "    edge [penwidth=30]\n" +
                "\n");

        graphvizAppendRecursive(sb, root, 1);

        sb.append("}");
        return sb.toString();
    }

    private int graphvizAppendRecursive(StringBuilder sb, NtdNode node, int nodeID) {
        String fillcolor;

        String label = "";
        String xlabel = "";
        String fontSize = String.valueOf(node.bag.size() * 10);

        switch (node.getNodeType()) {
            case LEAF -> {
                fillcolor = "#53A586";
                xlabel = String.valueOf(pathMaxBagSize.get(node));
            }
            case INTRODUCE -> {
                label = "+" + node.getSpecialVertex() + "\\n" + label;
                fillcolor = "#4FAED9";
            }
            case FORGET -> {
                fillcolor = "#C48F22";
                label = "-" + node.getSpecialVertex() + "\\n" + label;
            }
            case JOIN -> {
                fillcolor = "#CB534F";
                xlabel = String.valueOf(pathMaxBagSize.get(node));
            }
            default -> {
                fillcolor = "";
            }
        }
        sb.append(String.format("\t%d [label=\"%s\", fillcolor=\"%s\", xlabel=\"%s\", fontsize=\"%s\"];\n",
                nodeID,label,fillcolor,xlabel,fontSize));

        int maxNodeID = nodeID;
        if (node.getFirstChild() != null) {
            sb.append(String.format("\t%d --%d\n", nodeID, nodeID + 1));
            maxNodeID = graphvizAppendRecursive(sb, node.getFirstChild(), nodeID+1);

            if (node.getSecondChild() != null) {
                sb.append(String.format("\t%d --%d\n", nodeID, maxNodeID + 1));
                maxNodeID = graphvizAppendRecursive(sb, node.getSecondChild(), maxNodeID+1);
            }
        }
        return maxNodeID;
    }

    public Map<NtdNode, HashMap<Integer, Integer>> getPathIndicesMap() {
        return pathIndicesMap;
    }

    public Map<NtdNode, Integer> getPathMaxBagSize() {
        return pathMaxBagSize;
    }

    public NtdNode getRoot() {
        return root;
    }

    public int getTw() {
        return tw;
    }

    public int getNumberOfNodes() {
        return numberOfNodes;
    }

    public int getNumberOfJoinNodes() {
        return numberOfJoinNodes;
    }
}
