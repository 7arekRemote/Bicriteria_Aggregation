package multicriteriaSTCuts;

import datastructures.Ntd;
import datastructures.NtdIO;
import main.Main;
import multicriteriaSTCuts.benchmark.Benchmark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CorrectnessTester {
    static private final Logger logger = LoggerFactory.getLogger(CorrectnessTester.class);


    public static void testCorrectnessSingleComponent(MincutGraph mincutGraph, MincutSolver.DecomposerKind decomposerKind, boolean printAllPoss, boolean uniqueWeights) {
        
        
        logger.info("Dynprog solver is running...");
        List<Solution> dynprogSolutions = Benchmark.benchmarkSingleComponentMode(mincutGraph, null, MincutSolver.DecomposerKind.HEURISTIC_Fast, Benchmark.BenchmarkMode.STANDARD,1,1,-1, uniqueWeights, 0, "unknown");
        logger.info("Dynprog solver done.");
        
        logger.info("Bruteforce solver is running...");
        List<Solution> bruteforceSolutions = new BruteforceMincutSolver(mincutGraph).solveSingleComponent(false,uniqueWeights);
        logger.info("Bruteforce solver is done.");
        boolean isSame = compareSolutions(bruteforceSolutions, dynprogSolutions);
        if (!isSame) {
            logger.info("Bruteforce solutions:\n{}",bruteforceSolutions.stream().map(Object::toString).collect(Collectors.joining("\n")));
            logger.info("Dynprog solutions:\n{}",dynprogSolutions.stream().map(Object::toString).collect(Collectors.joining("\n")));
            
            if(printAllPoss)
                logger.info("Alle possibilities:\n{}",new BruteforceMincutSolver(mincutGraph).solveSingleComponent(true,uniqueWeights).stream().map(Object::toString).collect(Collectors.joining("\n")));
            

        }
    }

    public static void testCorrectnessAhrem(int decimals,boolean uniqueWeights, boolean useExistingNtd, MincutSolver.DecomposerKind decomposerKind) {
        boolean sameSolutions = true;
        for (int nr = 0; nr < 10; nr++) {
            
            MincutGraph mincutGraph = MincutGraphIO.readGraphFromTxt("res/graphs and ntds/ahrem/graphs/graph(" + nr + ").txt");

            Ntd ntd;
            if (useExistingNtd) {
                ntd = NtdIO.readNtd("res/graphs and ntds/ahrem/ntds/ntd(" + nr + ").txt", true);
            } else {
                ntd = MincutSolver.generateNtd(decomposerKind, mincutGraph);
            }

            
            mincutGraph.multiplyAllWeights(Math.pow(10,decimals));
            mincutGraph.roundAllWeights();

            logger.info("Starting bruteforce CC {}", nr);
            
            List<Solution> bruteforceSolutions = new BruteforceMincutSolver(mincutGraph).solveSingleComponent(false,uniqueWeights);

            logger.info("Starting dynprog CC {}", nr);
            
            List<Solution> dynprogSolutions = new MincutSolver(mincutGraph).solveSingleComponent(mincutGraph, ntd,-1,uniqueWeights);

            boolean compSameSolution = compareSolutions(bruteforceSolutions, dynprogSolutions);
            if (!compSameSolution) {
                logger.info("Bruteforce solutions:\n{}", bruteforceSolutions.stream().map(Object::toString).collect(Collectors.joining("\n")));
                logger.info("Dynprog solutions:\n{}",dynprogSolutions.stream().map(Object::toString).collect(Collectors.joining("\n")));
                sameSolutions = false;
            }
        }

        if (sameSolutions) {
            logger.info("Solutions match.");
        } else {
            logger.warn("Solutions DO NOT match.");
        }
    }

    public static boolean compareSolutions(List<Solution> bruteforceSolutions, List<Solution> dynprogSolutions) {
        boolean sameSolutions = true;
        logger.info("Comparing solutions...");
        if (bruteforceSolutions.size() != dynprogSolutions.size()) {
            logger.warn("Different number of solutions: Bruteforce: {}, Dynprog: {}", bruteforceSolutions.size(), dynprogSolutions.size());
            sameSolutions = false;
        }
        for (int i = 0; i < Math.min(bruteforceSolutions.size(),dynprogSolutions.size()); i++) {
            if (!Arrays.equals(bruteforceSolutions.get(i).getWeight(), dynprogSolutions.get(i).getWeight())) {
                logger.warn("Different weight: Index: {} Bruteforce: {}, Dynprog: {}",
                        i, bruteforceSolutions.get(i).getWeight(), dynprogSolutions.get(i).getWeight());
                sameSolutions = false;
            }
            if (!Objects.equals(bruteforceSolutions.get(i).getVertices(), dynprogSolutions.get(i).getVertices())) {
                logger.warn("Different vertices: Index: {} Bruteforce: {}, Dynprog: {}",
                        i, bruteforceSolutions.get(i).getVertices(), dynprogSolutions.get(i).getVertices());
                sameSolutions = false;
            }
        }

        if(!sameSolutions) Main.wrongSolutionAppeared = 1; 
        if(Main.wrongSolutionAppeared==-1) Main.wrongSolutionAppeared = 0; 

        logger.info("Comparing solutions done.");
        return sameSolutions;
    }
}
