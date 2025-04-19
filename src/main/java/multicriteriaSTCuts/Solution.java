package multicriteriaSTCuts;

import datastructures.Ntd;
import datastructures.NtdNode;
import multicriteriaSTCuts.dynamicProgamming.MincutSolutionVector;
import multicriteriaSTCuts.dynamicProgamming.SolutionPointer;

import java.util.*;

public class Solution implements Comparable<Solution>{
    private final double[] weight;
    private int[] vertices; 

    public Solution(double[] weight, ArrayList<Integer> vertices) {
        this.weight = weight;

        this.vertices = toArray(vertices);
    }

    public Solution(SolutionPointer solutionPointer, Ntd ntd) {
        ArrayList<Integer> vertexList = new ArrayList<>();
        recursiveReconstructSolution(solutionPointer, vertexList, ntd);
        Collections.sort(vertexList);

        vertices = toArray(vertexList);

        weight = solutionPointer.getWeight();
    }

    private void recursiveReconstructSolution(SolutionPointer solutionPointer, ArrayList<Integer> vertexList, Ntd ntd) {
        
        
        if (solutionPointer.ivMask != 0) {
            
            NtdNode node = ntd.nodeMap.get(solutionPointer.joinNodeId);

            
            List<Integer> sideIntroducedVertices = solutionPointer.isFromFirstChild ?
                    node.getFirstChildIntroducedVertices() : node.getSecondChildIntroducedVertices();

            List<Integer> S_side_introduced_vertices = MincutSolutionVector.reverseListMask(solutionPointer.ivMask, sideIntroducedVertices);

            for (int i = 0; i < S_side_introduced_vertices.size(); i++) {
                if(!vertexList.contains(S_side_introduced_vertices.get(i)))
                    vertexList.add(S_side_introduced_vertices.get(i));
            }
        }

        if(solutionPointer.getVertex() != null && !vertexList.contains(solutionPointer.getVertex()))
            vertexList.add(solutionPointer.getVertex());
        if(solutionPointer.getSolutionOrigin() != null)
            recursiveReconstructSolution(solutionPointer.getSolutionOrigin(),vertexList, ntd);
        if(solutionPointer.getSecondSolutionOrigin() != null)
            recursiveReconstructSolution(solutionPointer.getSecondSolutionOrigin(),vertexList, ntd);
    }


    public double[] getWeight() {
        return weight;
    }

    public ArrayList<Integer> getVertices() {
        
        ArrayList<Integer> verticesList = new ArrayList<>();
        for (int vertex : vertices) {
            verticesList.add(vertex);
        }
        return verticesList;
    }

    public void setVertices(ArrayList<Integer> vertices) {
        this.vertices = toArray(vertices);
    }

    @Override
    public String toString() {
        return "Gewicht: "+ Arrays.toString(weight) + ", \tKnoten: " + Arrays.toString(vertices);
    }

    @Override
    public int compareTo(Solution other) {
        for (int i = 0; i < weight.length; i++) {
            int compareResult = Double.compare(this.weight[i], other.weight[i]);
            if (compareResult != 0) {
                return compareResult;
            }
        }

        Iterator<Integer> thisIterator = this.getVertices().iterator();
        Iterator<Integer> otherIterator = other.getVertices().iterator();

        while (thisIterator.hasNext() && otherIterator.hasNext()) {
            int element1 = thisIterator.next();
            int element2 = otherIterator.next();

            if (element1 != element2) {
                return Integer.compare(element1, element2);
            }
        }

        return Integer.compare(this.getVertices().size(), other.getVertices().size());
    }

    private static int[] toArray(ArrayList<Integer> list) {
        int[] array = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}
