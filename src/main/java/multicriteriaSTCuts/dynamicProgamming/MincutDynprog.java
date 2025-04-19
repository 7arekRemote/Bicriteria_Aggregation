package multicriteriaSTCuts.dynamicProgamming;

import dataLogging.DataLog;
import dataLogging.RuntimeWatcher;
import datastructures.Ntd;
import datastructures.NtdNode;
import datastructures.NtdTransformer;
import main.Settings;
import multicriteriaSTCuts.Solution;
import multicriteriaSTCuts.benchmark.Benchmark;
import multicriteriaSTCuts.dynamicProgamming.outsourcing.OutsourceHandler;
import utils.ArrayMath;
import multicriteriaSTCuts.MincutGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static utils.ArrayMath.increaseArray;

public class MincutDynprog {

    private final Logger logger = LoggerFactory.getLogger(MincutDynprog.class);

    final Ntd ntd;
    public final MincutGraph mincutGraph;

    public final Stack<MincutSolutionVector> solutionVectorStack;


    private OutsourceHandler outsourceHandler;

    final boolean uniqueWeights;

    public int currentNodeNr = 0;


    public MincutDynprog(Ntd ntd, MincutGraph mincutGraph, boolean uniqueWeights) {
        this.ntd = ntd;
        this.mincutGraph = mincutGraph;
        this.uniqueWeights = uniqueWeights;
        solutionVectorStack = new Stack<>();

        if (ntd.getTw() > 31) {
            logger.error("Currently, only instances with tree width <= 31 can be solved.");
        }
    }

    public List<Solution> solve() {
        
        if (Settings.dataLogJoinDetailed) {
            DataLog.initJdDatasetFolder();
        }

        
        if (Settings.fuseJoinForgetNodes) {
            NtdTransformer.fuseJoinForgetNodes(ntd);
            if(Settings.fuseIntroduceJoinForgetNodes) {
                NtdTransformer.fuseIntroduceJoinForgetNodes(ntd);
            }
        }

        
        if (Settings.outsource) {

            
            try (OutsourceHandler handler = new OutsourceHandler(this) ) {
                outsourceHandler = handler;
                return actuallySolve();

            } catch (IOException e) {
                outsourceHandler = null;
                logger.error("IOException", e);
                throw new RuntimeException("IOException");
            }

        } else {
            return actuallySolve();
        }
    }

    private List<Solution> actuallySolve() {
        for (NtdNode node : ntd) {
            if(node.getNodeType() == NtdNode.NodeType.INTRODUCE_JOIN_FORGET) {
                currentNodeNr += node.getFirstChildIntroducedVertices().size(); 
                currentNodeNr += node.getSecondChildIntroducedVertices().size(); 
            }
            handleBag(node);
            currentNodeNr++;
            if(node.getNodeType() == NtdNode.NodeType.JOIN_FORGET) {
                currentNodeNr += node.getForgottenVertices().size(); 
            }
        }


        MincutSolutionVector solutionVector = solutionVectorStack.pop();
        if (!(solutionVectorStack.isEmpty())) {
            logger.error("The SolutionVectorStack is not empty:\n{}",solutionVectorStack);
            throw new RuntimeException("The SolutionVectorStack is not empty");
        }

        

        
        double[] initialWeight = new double[mincutGraph.getWeightDimension()];

        
        for (int v : mincutGraph.getJd_graph().getCopyOfVertices()) {
            increaseArray(initialWeight, mincutGraph.getEdgeWeight("s", v));
        }

        
        increaseArray(initialWeight, mincutGraph.getEdgeWeight("s", "t"));

        
        List<Solution> solutions = solutionVector.solutionArray.getSolutions(ntd);

        
        for (Solution solution : solutions) {
            ArrayMath.increaseArray(solution.getWeight(), initialWeight);
        }

        return solutions;
    }

    private void handleBag(NtdNode node) {
        
        if (Settings.outsource) {
            outsourceHandler.checkForPruning(node);
        }

        logger.trace(node.toString());
        
        String f_node_type = "";
        String s_node_type = "";
        String i_node_type;
        String f_solution_count = "";
        String s_solution_count = "";
        String i_solution_count;
        MincutSolutionVector first_sv = null;
        MincutSolutionVector second_sv = null;
        long startTime = System.nanoTime();

        if (node.getNodeType().toString().contains("JOIN")) {
            first_sv = solutionVectorStack.pop();
            f_solution_count = String.valueOf(first_sv.getNumSolutions());
            f_node_type = node.getFirstChild().getNodeType().toString();


            second_sv = solutionVectorStack.pop();
            s_solution_count = String.valueOf(second_sv.getNumSolutions());
            s_node_type = node.getSecondChild().getNodeType().toString();

        } else if (node.getNodeType() != NtdNode.NodeType.LEAF) {
            first_sv = solutionVectorStack.peek();
            f_solution_count = String.valueOf(first_sv.getNumSolutions());
            f_node_type = node.getFirstChild().getNodeType().toString();
        }

        switch (node.getNodeType()) {
            case LEAF -> {
                MincutSolutionVector new_sv = new MincutSolutionVector(this,
                        ntd.getPathIndicesMap().get(node), ntd.getPathMaxBagSize().get(node),
                        solutionVectorStack.size());
                new_sv.leaf();
                solutionVectorStack.push(new_sv);
            }
            case INTRODUCE -> first_sv.introduce(node);
            case FORGET -> first_sv.forget(node);
            case JOIN -> {
                MincutSolutionVector new_sv = new MincutSolutionVector(this,
                        ntd.getPathIndicesMap().get(node), ntd.getPathMaxBagSize().get(node),
                        first_sv, second_sv,
                        solutionVectorStack.size());
                new_sv.join(node, first_sv, second_sv);
                solutionVectorStack.push(new_sv); 
            }
            case JOIN_FORGET, INTRODUCE_JOIN_FORGET -> {
                MincutSolutionVector new_sv = new MincutSolutionVector(this,
                        ntd.getPathIndicesMap().get(node), ntd.getPathMaxBagSize().get(node),
                        first_sv, second_sv,
                        solutionVectorStack.size());
                new_sv.joinForget(node, first_sv, second_sv);
                solutionVectorStack.push(new_sv); 
            }
        }
        
        long endTime = System.nanoTime();
        i_solution_count = String.valueOf(solutionVectorStack.peek().getNumSolutions());
        int vi = solutionVectorStack.peek().getNumIntroducedVertices();
        int fi = solutionVectorStack.peek().getNumForgottenVertices();
        i_node_type = node.getNodeType().toString();
        int node_time = (int) ((endTime - startTime) / 1_000_000.0);

        if (Benchmark.currentResult != null) {
            long elapsedMs = (long) ((System.nanoTime() - Benchmark.currentResult.startTime) / 1_000_000.0);
            DataLog.node(String.format("%d,%d,%d,%d,%s,%s,%s,%d,%d,%s,%s,%s,%s,%s,%s,%d,%d,%d,%d", Benchmark.currentResult.graph_id, Benchmark.currentResult.ntd_nr,
                    currentNodeNr,
                    node.getBag().size(),
                    node.getFirstChildIntroducedVertices() != null ? String.valueOf(node.getFirstChildIntroducedVertices().size()) : "",
                    node.getSecondChildIntroducedVertices() != null ? String.valueOf(node.getSecondChildIntroducedVertices().size()) : "",
                    node.getNodeType().toString().contains("JOIN_FORGET") ? String.valueOf(node.getForgottenVertices().size()) : "",
                    vi, fi,
                    f_node_type, s_node_type, i_node_type,
                    f_solution_count, s_solution_count, i_solution_count,
                    RuntimeWatcher.getMaxHeapPercentage(),
                    node_time,
                    OutsourceHandler.firstFreeDEBUG, elapsedMs));
        }



        if (Math.pow(2, (node.getNodeType() == NtdNode.NodeType.JOIN_FORGET ? node.getBag().size() - node.getForgottenVertices().size(): node.getBag().size()))
                != solutionVectorStack.peek().solutionArray.getEntryCount()) {
            
            Set<Integer> positions = new HashSet<>();
            HashMap<Integer,Integer> pathIndex = ntd.getPathIndicesMap().get(node);

            HashSet<Integer> vertices = new HashSet<>(node.getBag());
            if(node.getNodeType() == NtdNode.NodeType.JOIN_FORGET || node.getNodeType() == NtdNode.NodeType.INTRODUCE_JOIN_FORGET)
                vertices.removeAll(node.getForgottenVertices());

            for(Integer vertex : vertices)
                positions.add(pathIndex.get(vertex));

            
            Set<Integer> targetNonNull = new HashSet<>();
            targetNonNull.add(0); 

            for (int pos : positions) {
                Set<Integer> newResult = new HashSet<>();
                for (int num : targetNonNull) {
                    int mask = 1 << pos; 
                    newResult.add(num | mask); 
                }
                targetNonNull.addAll(newResult); 
            }

            
            ArrayList<Integer> actualNonNull = new ArrayList<>();
            for (Iterator<Integer> it = solutionVectorStack.peek().solutionArray.getDebugNonNullIndexIterator(true, 0); it.hasNext(); ) {
                Integer index = it.next();
                actualNonNull.add(index);
            }
            Set<Integer> missingEntries = new HashSet<>(targetNonNull);
            missingEntries.removeAll(actualNonNull);
            Set<Integer> wrongEntries = new HashSet<>(actualNonNull);
            wrongEntries.removeAll(targetNonNull);

            if (!(missingEntries.isEmpty() && wrongEntries.isEmpty())) {
                logger.error("      Missing entries: {}",missingEntries);
                logger.error("  Superfluous entries: {}",wrongEntries);
                logger.error("       Entries should: {}",targetNonNull);
                logger.error("           Entries is: {}",actualNonNull);
                logger.error("            Positions: {}",positions);
            }

        }
        

    }

    public OutsourceHandler getOutsourceHandler() {
        return outsourceHandler;
    }
}
