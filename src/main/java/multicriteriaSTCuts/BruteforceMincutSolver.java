package multicriteriaSTCuts;

import improvements.Multithreader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.ArrayMath;

import java.util.*;


public class BruteforceMincutSolver {
    private final Logger logger = LoggerFactory.getLogger(BruteforceMincutSolver.class);

    MincutGraph mincutGraph;

    ArrayList<Integer> sortedVertices;

    public BruteforceMincutSolver(MincutGraph mincutGraph) {
        if(mincutGraph.getJd_graph().getNumVertices() > 30) {
            throw new IllegalArgumentException("The bruteforce algo is currently limited to num. vertices <= 30");
        }
        this.mincutGraph = mincutGraph;
        sortedVertices = new ArrayList<>(mincutGraph.getJd_graph().getCopyOfVertices());
        Collections.sort(sortedVertices);
    }

    public List<Solution> solveSingleComponent(boolean outputAllSolutions, boolean uniqueWeights) {
        List<SolutionPair> solutionPairs = new ArrayList<>((int) Math.pow(2,mincutGraph.getJd_graph().getNumVertices()));

        Multithreader multithreader = new Multithreader();

        
        for (int subsetMask = 0; subsetMask < (1 << mincutGraph.getJd_graph().getNumVertices()); subsetMask++) {

            SolutionPair solutionPair = new SolutionPair(mincutGraph.getWeightDimension());
            solutionPairs.add(solutionPair);

            int finalSubsetMask = subsetMask;
            multithreader.submit(() -> {
                computeSolutionPair(finalSubsetMask, solutionPair);
                return null;
            });
        }

        multithreader.waitForFinish();

        
        solutionPairs.sort(Comparator.comparingDouble((SolutionPair o) -> o.weight[0]).thenComparingDouble(o -> o.weight[1]));

        
        List<SolutionPair> poSolutionPairs = new ArrayList<>();

        poSolutionPairs.add(solutionPairs.get(0));
        double[] lastPOSWeight = solutionPairs.get(0).weight;

        for (int i = 1; i < solutionPairs.size(); i++) {
            if ((solutionPairs.get(i).weight[1] < lastPOSWeight[1]) ||
                    (!uniqueWeights && solutionPairs.get(i).weight[1] == lastPOSWeight[1] && solutionPairs.get(i).weight[0] == lastPOSWeight[0])) {

                
                lastPOSWeight = solutionPairs.get(i).weight;
                poSolutionPairs.add(solutionPairs.get(i));
            }
        }
        if(outputAllSolutions)
            poSolutionPairs = solutionPairs;

        
        List<Solution> solutions = new ArrayList<>(poSolutionPairs.size());
        for (SolutionPair solutionPair : poSolutionPairs) {
            ArrayList<Integer> vertices = new ArrayList<>();
            for (int i = 0; i < mincutGraph.getJd_graph().getNumVertices(); i++) {
                if ((solutionPair.subsetMask & (1 << i)) != 0) {
                    vertices.add(sortedVertices.get(i));
                }
            }
            solutions.add(new Solution(solutionPair.weight,vertices));
        }

        
        Collections.sort(solutions);

        return solutions;
    }

    private void computeSolutionPair(int subsetMask, SolutionPair solutionPair) {
        solutionPair.subsetMask = subsetMask;

        
        Set<Integer> subset = new HashSet<>();
        for (int i = 0; i < mincutGraph.getJd_graph().getNumVertices(); i++) {
            if ((subsetMask & (1 << i)) != 0) {
                subset.add(sortedVertices.get(i));
            }
        }

        
        
        ArrayMath.increaseArray(solutionPair.weight, mincutGraph.getEdgeWeight("s", "t"));

        
        for (int v : sortedVertices) {
            if (subset.contains(v)) {
                ArrayMath.increaseArray(solutionPair.weight, mincutGraph.getEdgeWeight("t", v));
            } else {
                ArrayMath.increaseArray(solutionPair.weight, mincutGraph.getEdgeWeight("s", v));
            }
        }

        
        for (int u : subset) {
            for (int v : mincutGraph.getJd_graph().getNeighborhood(u)) {
                if (subset.contains(v))
                    continue;
                ArrayMath.increaseArray(solutionPair.weight, mincutGraph.getEdgeWeight(u, v));
            }
        }
    }

    private static class SolutionPair {
        final double[] weight;
        int subsetMask;

        public SolutionPair(int weightDimension) {
            weight = new double[weightDimension];
        }
    }

}
