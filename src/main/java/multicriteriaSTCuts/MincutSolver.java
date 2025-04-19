package multicriteriaSTCuts;

import dataLogging.RuntimeWatcher;
import datastructures.ComputeHeuristicTD;
import datastructures.Ntd;
import datastructures.NtdIO;
import datastructures.NtdTransformer;
import jdrasil.algorithms.ExactDecomposer;
import jdrasil.algorithms.SmartDecomposer;
import jdrasil.algorithms.exact.*;
import jdrasil.algorithms.postprocessing.NiceTreeDecomposition;
import jdrasil.graph.Graph;
import jdrasil.graph.TreeDecomposition;
import jdrasil.graph.invariants.ConnectedComponents;
import main.Main;
import main.Settings;
import multicriteriaSTCuts.benchmark.Benchmark;
import multicriteriaSTCuts.dynamicProgamming.MincutDynprog;
import multicriteriaSTCuts.dynamicProgamming.SolutionPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.ArrayMath;

import java.util.*;

import static multicriteriaSTCuts.benchmark.Benchmark.saveSolutions;

public class MincutSolver {

    public enum DecomposerKind {
        EXACT_DEFAULT,
        EXACT_BranchAndBound,
        EXACT_BruteForceEliminationOrder,
        EXACT_CatchAndGlue,
        EXACT_CopsAndRobber,
        EXACT_DynamicProgramming,
        EXACT_LimitedGraphSearch,
        EXACT_PidBT,
        EXACT_SAT_BASE,
        EXACT_SAT_IMPROVED,
        HEURISTIC_Smart,
        HEURISTIC_Fast
    }

    private static final Logger logger = LoggerFactory.getLogger(MincutSolver.class);

    MincutGraph originalMincutGraph;

    public MincutSolver(MincutGraph mincutGraph) {
        this.originalMincutGraph = mincutGraph;
    }

    public List<List<Solution>> solve(DecomposerKind decomposerKind,boolean uniqueWeights) {

        
        List<MincutGraph> allCCs = getConnComps(originalMincutGraph);

        
        List<List<Solution>> allCCSolutions = new ArrayList<>();

        for (int i = 0; i < allCCs.size(); i++) {
            logger.info("[{}/{}] Solving CC...",i+1,allCCs.size());
            MincutGraph cc = allCCs.get(i);
            List<Solution> singleCCSolutions = solveSingleComponent(decomposerKind,cc,uniqueWeights);
            allCCSolutions.add(singleCCSolutions);
            logger.info("[{}/{}] CC solutions calculated.",i+1,allCCs.size());

        }

        return allCCSolutions;
    }

    public static List<MincutGraph> getConnComps(MincutGraph originalMincutGraph) {
        logger.info("Connected components are determined...");
        Set<Graph<Integer>> subGraphs = new ConnectedComponents<>(originalMincutGraph.getJd_graph()).getAsSubgraphs();

        
        List<MincutGraph> mincutGraphs = new ArrayList<>();

        for (Graph<Integer> subGraph : subGraphs) {
            MincutGraph subMincutGraph = new MincutGraph(originalMincutGraph, subGraph);
            mincutGraphs.add(subMincutGraph);
        }

        
        mincutGraphs.sort(Comparator.comparingInt(o -> o.getJd_graph().getNumVertices()));

        
        if (Settings.saveNewMincutGraphs) {
            for (MincutGraph subMincutGraph : mincutGraphs) {
                MincutGraphIO.writeGraphToTxt(subMincutGraph,String.format(Main.sessionOutputFolder + "/mincutGraphs/graph.txt"));
            }
        }

        
        if (logger.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("CC_size\tVertices_IDs\n");
            for (MincutGraph subGraph : mincutGraphs) {
                Set<Integer> vertices = subGraph.getJd_graph().getCopyOfVertices();
                sb.append(vertices.size()).append("\t");
                sb.append(vertices).append("\n");
            }
            logger.debug("CCs:\n{}",sb);
        }

        logger.info("CC determination finished.");
        return mincutGraphs;
    }

    private List<Solution> solveSingleComponent(DecomposerKind decomposerKind, MincutGraph mincutGraph,boolean uniqueWeights) {
        
        Ntd ntd = generateNtd(decomposerKind, mincutGraph);

        List<Solution> solutionVector = solveSingleComponent(mincutGraph, ntd,-1,uniqueWeights);

        return solutionVector;
    }

    public List<Solution> solveSingleComponent(MincutGraph mincutGraph, Ntd ntd, int decimals, boolean uniqueWeights) {
        logger.info("Dynamic programming is being executed...");

        
        if (decimals != -1) { 
            mincutGraph.multiplyAllWeights(Math.pow(10, decimals));
            mincutGraph.roundAllWeights();
        }


        
        MincutDynprog mincutDynprog = new MincutDynprog(ntd, mincutGraph,uniqueWeights);
        List<Solution> solutions = mincutDynprog.solve();

        
        Collections.sort(solutions);

        
        if (decimals != -1) {
            for (Solution solution : solutions) {
                ArrayMath.multiplyArray(solution.getWeight(),Math.pow(10,-decimals));
                ArrayMath.round(solution.getWeight(),decimals);
            }
        }

        logger.info("Dynamic programming is done.");

        if (Settings.saveSolutions) {
            if (Benchmark.currentResult != null) {
                saveSolutions(solutions, String.format(Main.sessionOutputFolder + "/solutions/id%d_nr%d.txt",
                        Benchmark.currentResult.graph_id, Benchmark.currentResult.ntd_nr),
                        mincutGraph.idOffset);
            } else {
                saveSolutions(solutions, String.format(Main.sessionOutputFolder + "/solutions/n%d_m%d.txt",
                        mincutGraph.getJd_graph().getNumVertices(), mincutGraph.getJd_graph().getNumberOfEdges()),
                        mincutGraph.idOffset);
            }
        }

        return solutions;
    }


    public static Ntd generateNtd(DecomposerKind decomposerKind, MincutGraph mincutGraph) {
        try {
            
            RuntimeWatcher.resetStatistics();
            long startTime = System.nanoTime();

            
            logger.info("jd_unprocessed_td is generated ({})...", decomposerKind.toString());
            TreeDecomposition<Integer> jd_unprocessed_td = null;
            switch (decomposerKind) {
                case EXACT_DEFAULT -> {
                    jd_unprocessed_td = new ExactDecomposer<>(mincutGraph.getJd_graph()).call();
                }
                case EXACT_BranchAndBound -> {
                    jd_unprocessed_td = new BranchAndBoundDecomposer<>(mincutGraph.getJd_graph()).call();
                }
                case EXACT_BruteForceEliminationOrder -> {
                    jd_unprocessed_td = new BruteForceEliminationOrderDecomposer<>(mincutGraph.getJd_graph()).call();
                }
                case EXACT_CatchAndGlue -> {
                    jd_unprocessed_td = new CatchAndGlue<>(mincutGraph.getJd_graph()).call();
                }
                case EXACT_CopsAndRobber -> {
                    jd_unprocessed_td = new CopsAndRobber<>(mincutGraph.getJd_graph()).call();
                }
                case EXACT_DynamicProgramming -> {
                    jd_unprocessed_td = new DynamicProgrammingDecomposer<>(mincutGraph.getJd_graph()).call();
                }
                case EXACT_LimitedGraphSearch -> {
                    jd_unprocessed_td = new LimitedGraphSearch<>(mincutGraph.getJd_graph()).call();
                }
                case EXACT_PidBT -> {
                    jd_unprocessed_td = new PidBT<>(mincutGraph.getJd_graph()).call();
                }
                case EXACT_SAT_BASE -> {
                    jd_unprocessed_td = new SATDecomposer<>(mincutGraph.getJd_graph(), SATDecomposer.Encoding.BASE).call();
                }
                case EXACT_SAT_IMPROVED -> {
                    jd_unprocessed_td = new SATDecomposer<>(mincutGraph.getJd_graph(), SATDecomposer.Encoding.IMPROVED).call();
                }
                case HEURISTIC_Smart -> {
                    jd_unprocessed_td = new SmartDecomposer<>(mincutGraph.getJd_graph()).call();
                }
                case HEURISTIC_Fast -> {
                    jd_unprocessed_td = new ComputeHeuristicTD(mincutGraph.getJd_graph()).computeTD();
                }
            }
            logger.info("jd_unprocessed_td generation done.");

            
            logger.info("jd_ntd is generated...");
            NiceTreeDecomposition<Integer> jd_ntd = new NiceTreeDecomposition<>(jd_unprocessed_td, false);
            jd_ntd.getProcessedTreeDecomposition();
            logger.info("jd_ntd generation done.");


            
            long endTime = System.nanoTime();
            if (Benchmark.currentResult != null) {
                Benchmark.currentResult.ntd_max_cpu_diff = (int) RuntimeWatcher.getMaxCpuDiff();
                Benchmark.currentResult.jd_max_heap_usage = RuntimeWatcher.getMaxHeapMiB();
                Benchmark.currentResult.jd_total_time = (int) ((endTime - startTime) / 1_000_000.0);
            }

            
            logger.info("ntd is generated...");
            Ntd ntd = NtdTransformer.getNtdFromJdNtd(jd_ntd);
            logger.info("ntd generation done.");

            if (Settings.saveNewNtds) {
                NtdIO.writeNtd(ntd,String.format(Main.sessionOutputFolder + "/ntds/ntd.txt"));
            }

            return ntd;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
