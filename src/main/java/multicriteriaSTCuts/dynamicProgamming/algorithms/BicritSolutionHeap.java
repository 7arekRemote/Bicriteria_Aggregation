package multicriteriaSTCuts.dynamicProgamming.algorithms;

import dataLogging.DataLog;
import multicriteriaSTCuts.benchmark.Benchmark;
import utils.ArrayMath;
import multicriteriaSTCuts.dynamicProgamming.SolutionPointer;

import java.util.*;

import static utils.ArrayMath.isLess;

public abstract class BicritSolutionHeap {
    protected List<SolutionPointer> aSolutions;
    protected List<SolutionPointer> bSolutions;
    protected HeapNode[] heap;
    protected int size;

    protected boolean swappedArrays;

    protected double[] weightOverlap;

    
    public int num_heapify = 0;
    protected final int childSBinary;
    protected final int nodeNr;
    protected int recursion_level = 0;


    public BicritSolutionHeap(List<SolutionPointer> firstSolutions, List<SolutionPointer> secondSolutions,
                              double[] weightOverlap, boolean initHeap, int nodeNr, int childSBinary,
                              int recursion_level) {
        this.nodeNr = nodeNr;
        this.recursion_level = recursion_level;
        this.childSBinary = childSBinary;

        
        if (firstSolutions.size() <= secondSolutions.size()) {
            this.aSolutions = firstSolutions;
            this.bSolutions = secondSolutions;
            swappedArrays = false;
        } else {
            this.bSolutions = firstSolutions;
            this.aSolutions = secondSolutions;
            swappedArrays = true;
        }

        this.weightOverlap = weightOverlap;

        if (initHeap) {
            initHeap(childSBinary);
        }

    }

    protected void initHeap(int childSBinary) {
        heap = createHeapNodes().toArray(new HeapNode[0]);

        
        for (int i = (size - 2) / 2; i >= 0; i--) {
            minHeapify(i);
        }
    }

    protected BicritSolutionHeap(int nodeNr, int childSBinary,int recursion_level) { 
        this.childSBinary = childSBinary;
        this.nodeNr = nodeNr;
        this.recursion_level = recursion_level;
    }

    protected abstract List<? extends HeapNode> createHeapNodes();

    protected void minHeapify(int i) {
        num_heapify++;

        int l = 2 * i + 1;
        int r = l + 1;
        int smallest = i;
        if(l < size && isLess(heap[l].weight,heap[smallest].weight))
            smallest = l;
        if(r < size && isLess(heap[r].weight,heap[smallest].weight))
            smallest = r;
        if (smallest != i) {
            HeapNode tmp = heap[i];
            heap[i] = heap[smallest];
            heap[smallest] = tmp;

            minHeapify(smallest);
        }
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public double[] getMinWeight() {
        return heap[0].weight;
    }

    
    public SolutionPointer getMinSolution() {
        return heap[0].createSolutionPointer();
    }

    public void removeMinAndAddNext() {
        if (!heap[0].increasePointerAndWeight()) {
            
            size--;
            heap[0] = heap[size];
        }
        minHeapify(0);
    }

    
    protected double[] getAddedWeight(int aSolutionIdx, int bSolutionIdx) {
        double[] addedWeight = Arrays.copyOf(aSolutions.get(aSolutionIdx).getWeight(), aSolutions.get(aSolutionIdx).getWeight().length);
        ArrayMath.increaseArray(addedWeight, bSolutions.get(bSolutionIdx).getWeight());
        ArrayMath.increaseArray(addedWeight, weightOverlap, -1);
        return addedWeight;
    }

    public void doHeuristicDatalog(int numNewSolutions, int mainChildSBinary) {
        DataLog.heuristicNode(String.format(Locale.US,"%d,%d,%d,%s,%d,%s,%s,%s,%s,%s,%d,%d,%d,%d",
                Benchmark.currentResult.graph_id, nodeNr, childSBinary,
                mainChildSBinary == -1 ? "" : String.valueOf(mainChildSBinary),
                recursion_level,
                "no",
                "","","","",
                num_heapify,
                aSolutions.size(),bSolutions.size(),
                numNewSolutions));
    }

    public ArrayList<SolutionPointer> exhaustHeapSolutions(boolean useUniqueWeights) {
        ArrayList<SolutionPointer> newSolutions = new ArrayList<>();
        double[] lastPOSWeight = new double[]{Double.MAX_VALUE, Double.MAX_VALUE};
        while (!this.isEmpty()) {
            if(Thread.interrupted()) throw new RuntimeException();

            double[] weight = this.getMinWeight();
            if ((weight[1] < lastPOSWeight[1]) ||
                    (!useUniqueWeights && weight[1] == lastPOSWeight[1] && weight[0] == lastPOSWeight[0])) {
                
                SolutionPointer solution = this.getMinSolution();
                newSolutions.add(solution);
                lastPOSWeight = weight;
            }
            this.removeMinAndAddNext();
        }
        return newSolutions;
    }

    public ArrayList<double[]> exhaustHeapPoints(boolean useUniqueWeights) {
        ArrayList<double[]> newPoints = new ArrayList<>();
        double[] lastPOSWeight = new double[]{Double.MAX_VALUE, Double.MAX_VALUE};
        while (!this.isEmpty()) {
            double[] weight = this.getMinWeight();
            if ((weight[1] < lastPOSWeight[1]) ||
                    (!useUniqueWeights && weight[1] == lastPOSWeight[1] && weight[0] == lastPOSWeight[0])) {
                
                newPoints.add(weight);
                lastPOSWeight = weight;
            }
            this.removeMinAndAddNext();
        }
        return newPoints;
    }


    protected abstract static class HeapNode {

        final int aSolutionIdx;
        int bSolutionIdx;
        double[] weight;

        public HeapNode(int aSolutionIdx, double[] weight) {
            this.aSolutionIdx = aSolutionIdx;
            bSolutionIdx = 0;
            this.weight = weight;
        }

        protected abstract SolutionPointer createSolutionPointer();

        
        protected abstract boolean increasePointerAndWeight();
    }
}
