package multicriteriaSTCuts.dynamicProgamming.algorithms;

import multicriteriaSTCuts.dynamicProgamming.SolutionPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static multicriteriaSTCuts.dynamicProgamming.algorithms.HeuristicBicritSolutionHeap.*;

public class FusedBicritSolutionHeap extends BicritSolutionHeap {
    static private final Logger logger = LoggerFactory.getLogger(FusedBicritSolutionHeap.class);
    
    private final List<BicritSolutionHeap> heapList;
    private final boolean useHeuristic;
    private double avg_poCalcSize_percent;

    public FusedBicritSolutionHeap(boolean useHeuristic, List<BicritSolutionHeap> heapList, int mainChildSBinary, int nodeNr, int recursion_level) {
        super(nodeNr,mainChildSBinary,recursion_level);
        this.heapList = heapList;
        this.useHeuristic = useHeuristic;
        initHeap(mainChildSBinary);
    }

    @Override
    protected List<? extends HeapNode> createHeapNodes() {
        List<HeapNode> heapNodes = new LinkedList<>();

        
        if (useHeuristic) {

            List<double[]> po_heuristic_points = poHeuristicFunc(heapList);

            for (BicritSolutionHeap heap : heapList) {
                heapNodes.addAll(((HeuristicBicritSolutionHeap) heap).createHeapNodes(po_heuristic_points));
                ((HeuristicBicritSolutionHeap) heap).poCalcSize_percent = avg_poCalcSize_percent;
                heap.doHeuristicDatalog(-1, childSBinary);
            }
        } else {
            
            for (BicritSolutionHeap heap : heapList) {
                heapNodes.addAll(heap.createHeapNodes());
                heap.doHeuristicDatalog(-1, childSBinary);
            }
        }
        size = heapNodes.size();
        return heapNodes;
    }

    private List<double[]> poHeuristicFunc(List<BicritSolutionHeap> heapList) {
        
        List<BicritSolutionHeap> smallHeapList = new ArrayList<>(heapList.size());

        int poCalcPoints = 0;

        ArrayList<List<SolutionPointer>[]> reducedSolutionsLists = new ArrayList<>();
        for (int i = 0; i < heapList.size(); i++) {
            List<SolutionPointer> aLowerPoints;
            List<SolutionPointer> bLowerPoints;
            if (recursion_level ==0 ) {
                aLowerPoints = extremePointsOfSegments(heapList.get(i).aSolutions, numPoHeuristicSegments, segmentMinCount);
                bLowerPoints = extremePointsOfSegments(heapList.get(i).bSolutions, numPoHeuristicSegments, segmentMinCount);
            } else {
                
                aLowerPoints = getSubsamplePoints(heapList.get(i).aSolutions, recursiveSumbsampleRate);
                bLowerPoints = getSubsamplePoints(heapList.get(i).bSolutions, recursiveSumbsampleRate);
            }
            reducedSolutionsLists.add(new List[]{aLowerPoints, bLowerPoints});
        }

        
        double reducedASolutionsAvg = (double) reducedSolutionsLists.stream().mapToInt(lists -> lists[0].size()).sum() / reducedSolutionsLists.size();
        double reducedBSolutionsAvg = (double) reducedSolutionsLists.stream().mapToInt(lists -> lists[1].size()).sum() / reducedSolutionsLists.size();
        
        int recursive_subsample_max = (int) Math.floor(1 / (float) recursiveSumbsampleRate * Math.max(reducedASolutionsAvg, reducedBSolutionsAvg));
        boolean solveRecursively = lbMinCount * 2 <= recursive_subsample_max && recursiveSumbsampleRate >1;

        for (int i = 0; i < reducedSolutionsLists.size(); i++) {
            List<SolutionPointer> aLowerPoints = reducedSolutionsLists.get(i)[0];
            List<SolutionPointer> bLowerPoints = reducedSolutionsLists.get(i)[1];

            poCalcPoints += aLowerPoints.size() * bLowerPoints.size();
            BicritSolutionHeap smallHeap = solveRecursively ?
                new HeuristicBicritSolutionHeap(aLowerPoints, bLowerPoints, heapList.get(i).weightOverlap, false, nodeNr, heapList.get(i).childSBinary, recursion_level+1) :
                new NormalBicritSolutionHeap(aLowerPoints, bLowerPoints, heapList.get(i).weightOverlap, false, nodeNr, heapList.get(i).childSBinary, recursion_level+1);

            smallHeapList.add(smallHeap);
        }

        
        long allPointCombSum = heapList.stream().mapToInt(heap -> heap.aSolutions.size() * heap.bSolutions.size()).sum();
        avg_poCalcSize_percent = (double) poCalcPoints / allPointCombSum;

        
        FusedBicritSolutionHeap smallFusedHeap = new FusedBicritSolutionHeap(solveRecursively, smallHeapList,childSBinary, nodeNr,recursion_level+1);
        List<double[]> heuristicPoints = smallFusedHeap.exhaustHeapPoints(true);

        return heuristicPoints;
    }

}
