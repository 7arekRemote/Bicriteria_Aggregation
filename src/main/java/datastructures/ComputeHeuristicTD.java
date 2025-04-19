package datastructures;

/*
 * Copyright (c) 2016-2017, Max Bannach, Sebastian Berndt, Thorsten Ehlers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
 * OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import jdrasil.algorithms.preprocessing.GraphReducer;

import jdrasil.algorithms.upperbounds.LocalSearchDecomposer;
import jdrasil.algorithms.upperbounds.PaceGreedyDegreeDecomposer;
import jdrasil.algorithms.upperbounds.StochasticGreedyPermutationDecomposer;
import jdrasil.graph.Bag;
import jdrasil.graph.Graph;
import jdrasil.graph.GraphFactory;
import jdrasil.graph.TreeDecomposition;
import jdrasil.utilities.JdrasilProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Jdrasil is a program to compute a small tree-decomposition of a given graph.
 * It is developed at the Universitaet zu Luebeck in context of the PACE challenge (www.pacechallenge.wordpress.com).
 *
 * <p>
 * This class provides an entry point to the program for computing heuristical tree decompositions (no guaranteed or
 * bound is provided).
 * </p>
 *
 * <p>
 *     Unless the parameter -instant is given, the program will run until it explicitly receives a SIGTERM kill.
 * </p>
 *
 * @author Max Bannach
 * @author Sebastian Berndt
 * @author Thorsten Ehlers
 */
public class ComputeHeuristicTD {

    /** Jdrasils Logger */
    private final static Logger logger = LoggerFactory.getLogger(ComputeHeuristicTD.class);

    /** Start and end of the computation. */
    private long tstart, tend;

    /** The graph to be decomposed. */
    private Graph<Integer> normalizedInput;
    private Graph<Integer> input;

    Map<Integer, Integer> vertexMapping;

    Integer[] invVertexMapping;


    /** The decomposition in construction. */
    private TreeDecomposition<Integer> decomposition;

    /** The reducer used to preprocess the graph. */
    private GraphReducer<Integer> reducer;

    /** The stochastic greedy permutation decomposer used in the first phase */
    private StochasticGreedyPermutationDecomposer<Integer> greedyPermutationDecomposer;

    /** The local search decomposer used in the third phase */
    private LocalSearchDecomposer<Integer> localSearchDecomposer;

    public static volatile boolean shutdownFlag;

    public ComputeHeuristicTD(Graph<Integer> input) {
        this.input = input;
    }

    public TreeDecomposition<Integer> computeTD() {
        JdrasilProperties.setProperty("parallel", "");
        JdrasilProperties.setProperty("seed",""+System.currentTimeMillis());

        this.normalizedInput = normalizeGraph(input);


        try {
            int upperBound = normalizedInput.getNumVertices();
            boolean needsPostProcessing = false;
            List<Integer> perm = null;
            for(int i = 0 ; i < 30 && !JdrasilProperties.timeout() && !ComputeHeuristicTD.shutdownFlag ; i++){
                PaceGreedyDegreeDecomposer pcdd = new PaceGreedyDegreeDecomposer(normalizedInput);
                TreeDecomposition<Integer> td =  pcdd.computeTreeDecomposition(upperBound);
                if(td != null && td.getWidth() < upperBound){
                    this.decomposition = td;
                    logger.info("td.getWidth(): {}", td.getWidth());
                    upperBound = td.getWidth();
                    if(i > 3 && upperBound < 1000)
                        break;
                }
            }
            if(!ComputeHeuristicTD.shutdownFlag && !JdrasilProperties.containsKey("instant")){
                /* Compute an explicit decomposition */
                tstart = System.nanoTime();


//                logger.info("reducing the graph");
//                reducer = new GraphReducer<>(normalizedInput);
//                logger.info("Step 1");
//                Graph<Integer> reduced = reducer.getProcessedGraph();
//                logger.info("Step 2:");
//                if (reduced.getCopyOfVertices().size() > 0) {
                if(true){
//                    logger.info("reduced the graph to " + reduced.getCopyOfVertices().size() + " vertices");

                    // temporary tree decomposition to avoid raise conditions
                    TreeDecomposition<Integer> tmp;

                    logger.info("Starting greedy permutation phase");

                    greedyPermutationDecomposer = new StochasticGreedyPermutationDecomposer<>(normalizedInput);
                    //greedyPermutationDecomposer.setUpper_bound(upperBound);
                    tmp = greedyPermutationDecomposer.call();
                    logger.info("Step 3");
                    synchronized (this) {
                        if(this.decomposition == null ||
                                (tmp != null && tmp.getWidth() < this.decomposition.getWidth())){
                            this.decomposition = tmp;
                            needsPostProcessing = true;
                            logger.info("Found better 2");
                            logger.info("Width = " + this.decomposition.getWidth());
                            return getSolution(this.decomposition, needsPostProcessing);
                        }
                    }
                    //                if(!JdrasilProperties.timeout() ){
                    //	                LOG.info("Improving the decomposition");
                    //	                tmp = this.decomposition.copy();
                    //	                tmp.improveDecomposition();
                    //	                synchronized (this) {
                    //	                    this.decomposition = tmp;
                    //	                }
                    //                }

                    // we may skip the local search phase
                    //if (!Heuristic.shutdownFlag &&  !JdrasilProperties.timeout() &&  !JdrasilProperties.containsKey("instant")) {

                    int numLocalSearch = 10;
                    while (numLocalSearch > 0) {
                        numLocalSearch--;
                        logger.info("Starting local search phase");
                        if(greedyPermutationDecomposer.getPermutation() != null)
                            perm = greedyPermutationDecomposer.getPermutation();
                        localSearchDecomposer = new LocalSearchDecomposer<>(normalizedInput, 1, 31, perm);
                        tmp = localSearchDecomposer.call();
                        logger.info("Local Search Done:" + this.decomposition.getWidth());
                        synchronized (this) {
                            if(this.decomposition == null ||
                                    (tmp != null && tmp.getWidth() < this.decomposition.getWidth())){
                                this.decomposition = tmp;
                                logger.info("Found better");
                                needsPostProcessing = true;
                                logger.info("Width = " + this.decomposition.getWidth());
                                return getSolution(this.decomposition, needsPostProcessing);
                            }
                        }

                    }
                }
            }
            // print and exit
            logger.info("1Width = " + this.decomposition.getWidth());
            return getSolution(this.decomposition, true);

        } catch (Exception e) {
            logger.error("Error during the computation of the decomposition.",e);
            return null;
        }
    }

    private Graph<Integer> normalizeGraph(Graph<Integer> inputGraph) {
//        logger.info("Normalisiere Graph...");

        Graph<Integer> normalizedGraph = GraphFactory.emptyGraph();
        Set<Integer> inputVertices = inputGraph.getCopyOfVertices();

        vertexMapping = new HashMap<>();
        invVertexMapping = new Integer[inputVertices.size()];

        int newVertexValue = 0;

        for (Integer originalVertex : inputVertices) {
            normalizedGraph.addVertex(newVertexValue);

            vertexMapping.put(originalVertex, newVertexValue);
            invVertexMapping[newVertexValue] = originalVertex;

            newVertexValue++;
        }

        for (Integer originalVertex : inputVertices) {
            Set<Integer> neighbors = inputGraph.getNeighborhood(originalVertex);

            for (Integer neighbor : neighbors) {
                int normalizedVertex = vertexMapping.get(originalVertex);
                int normalizedNeighbor = vertexMapping.get(neighbor);

                normalizedGraph.addEdge(normalizedVertex, normalizedNeighbor);
            }
        }

//        logger.info("Normalisieren fertig.");
        return normalizedGraph;
    }

    private void deNormalizeDecomposition(TreeDecomposition<Integer> normalizedTD) {

        normalizedTD.setGraph(input);

        for (Bag<Integer> normalizedBag : normalizedTD.getTree().getAdjacencies().keySet()) {

            Set<Integer> deNormalizedSet = new HashSet<>();
            for (Integer normalizedInt : normalizedBag.vertices) {
                deNormalizedSet.add(invVertexMapping[normalizedInt]);
            }
            normalizedBag.vertices = deNormalizedSet;
        }
    }

    /**
     * This method undoes the preprocessing to create a final tree decompositions and prints this decomposition to std.out.
     * This method will exit the program.
     * @param td a tree decomposition of the reduced graph
     */
    private synchronized TreeDecomposition<Integer> getSolution(TreeDecomposition<Integer> td, boolean needsPostProcessing) {
//        if(needsPostProcessing){
//            if (reducer.getProcessedGraph().getCopyOfVertices().size() != 0) reducer.addbackTreeDecomposition(td);
//            this.decomposition = reducer.getTreeDecomposition();
//        }

//        NiceTreeDecomposition<Integer> nice = new NiceTreeDecomposition<Integer>(this.decomposition,false);
//        this.decomposition = nice.getProcessedTreeDecomposition();

        tend = System.nanoTime();
        deNormalizeDecomposition(this.decomposition);
        return this.decomposition;

//        Ntd ntd = NtdTransformer.getNtdFromJdNtd(nice);
//        File outputFile = NtdIO.writeNtd(ntd,"output/ntds/computeHeuristicTD.txt");
//        logger.info("computeHeuristicTD: TW {}, Datei: {}", ntd.getTw(), outputFile.getName());


    }
}