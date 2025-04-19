package multicriteriaSTCuts.benchmark;

import bicriteriaAggregation.AggregationSolver;
import dataLogging.DataLog;
import dataLogging.RuntimeWatcher;
import datastructures.Ntd;
import datastructures.NtdIO;
import datastructures.NtdNode;
import datastructures.NtdTransformer;
import improvements.Mailer;
import improvements.Multithreader;
import main.Main;
import main.Settings;
import multicriteriaSTCuts.MincutGraph;
import multicriteriaSTCuts.MincutGraphIO;
import multicriteriaSTCuts.MincutSolver;
import multicriteriaSTCuts.Solution;
import multicriteriaSTCuts.rootChoosing.Simulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Diagnostics;
import utils.IO;

import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static multicriteriaSTCuts.MincutSolver.generateNtd;

public class Benchmark {
    static private final Logger logger = LoggerFactory.getLogger(Benchmark.class);

    public static int current_graph_id = -1;
    public static int current_ntd_nr = 0;

    public static Result currentResult = null;
    public static Thread currentBenchmarkThread;
    public static String currentDatasetName;

    public enum BenchmarkMode {
        STANDARD,
        ALL_ROOTS,
        MULTIPLE_NTD

    }

    public static void generateMultipleNtdsAndGraphs(File mainFolder, MincutSolver.DecomposerKind decomposerKind) {
        
        boolean tmpSaveNtds = Settings.saveNewNtds;
        boolean tmpSaveGraphs = Settings.saveNewMincutGraphs;
        Settings.saveNewNtds = false;
        Settings.saveNewMincutGraphs = false;


        File[] subFolders = mainFolder.listFiles(File::isDirectory);
        Arrays.sort(subFolders);


        Multithreader multithreader = new Multithreader();

        for (int i = 0; i <subFolders.length; i++) {


            File folder = subFolders[i];
            String datasetName = folder.getName();
            logger.info("Calculate graphs and NTDs of {} [{}/{}]",datasetName, i +1,subFolders.length);

            multithreader.submit(() -> {
                generateNtdsAndGraphs(decomposerKind, folder);
                return null;
            });

        }

        multithreader.waitForFinish();

        Settings.saveNewNtds = tmpSaveNtds;
        Settings.saveNewMincutGraphs = tmpSaveGraphs;
    }

    public static void generateNtdsAndGraphs(MincutSolver.DecomposerKind decomposerKind, File datasetFolder) {
        
        boolean tmpSaveNtds = Settings.saveNewNtds;
        boolean tmpSaveGraphs = Settings.saveNewMincutGraphs;
        Settings.saveNewNtds = false;
        Settings.saveNewMincutGraphs = false;

        
        File verticesFile = new File(datasetFolder.getAbsoluteFile() + "/" + "vertecies.csv");
        File adjacenciesFile = new File(datasetFolder.getAbsoluteFile() + "/" + "adjacencies.csv");

        MincutGraph originalMincutGraph = MincutGraphIO.readGraphFromDataset(verticesFile, adjacenciesFile);

        
        List<MincutGraph> allCCs = MincutSolver.getConnComps(originalMincutGraph);

        
        String datasetName = datasetFolder.getName();
        String test = Main.sessionOutputFolder + "/graphs and ntds/" + datasetName;
        String datasetOutputFolder = IO.getFreeFolderName(test).getAbsolutePath();


        
        for (MincutGraph subMincutGraph : allCCs) {
            MincutGraphIO.writeGraphToTxt(subMincutGraph,String.format(datasetOutputFolder+ "/graphs/graph.txt"));
        }

        
        for (MincutGraph subMincutGraph : allCCs) {
            Ntd ntd = generateNtd(decomposerKind, subMincutGraph);
            NtdIO.writeNtd(ntd,String.format(datasetOutputFolder + "/ntds/ntd.txt"));
        }

        Settings.saveNewNtds = tmpSaveNtds;
        Settings.saveNewMincutGraphs = tmpSaveGraphs;
    }


    public static void benchmarkDataset(String datasetsFolder, String datasetName, int decimals, boolean uniqueWeights, MincutSolver.DecomposerKind decomposerKind, boolean onlyBiggestComponent, BenchmarkMode mode, int maxRootCount, int ntdCount, boolean useGivenNtd, long maxDynprogMs) {
        List<List<Solution>> solutions;
        currentDatasetName = datasetName;
        logger.info("Benchmarking of {} started...",datasetName);

        if (useGivenNtd) {
            solutions = new ArrayList<>();

            
            int ccCount = 0;
            for (int i = 0; true; i++) {
                File test = new File(datasetsFolder + "/"+ datasetName +"/graphs/graph("+i+").txt");
                if (!test.exists()) {
                    ccCount = i;
                    break;
                }
            }

            
            for (int i = 0; i < ccCount; i++) {
                
                if (onlyBiggestComponent && i != ccCount - 1) {
                    continue;
                }

                logger.info("[{}/{}] Solving CC...",i+1,ccCount);

                
                Ntd ntd;
                if(useGivenNtd)
                    ntd = NtdIO.readNtd(datasetsFolder+ "/"+ datasetName + "/ntds/ntd("+i+").txt",true);
                else
                    ntd = null;

                
                MincutGraph mincutGraph = MincutGraphIO.readGraphFromTxt(datasetsFolder+ "/"+ datasetName + "/graphs/graph("+i+").txt");

                
                List<Solution> singleCCSolutions = benchmarkSingleComponentMode(mincutGraph, ntd,decomposerKind, mode,maxRootCount,ntdCount, decimals,uniqueWeights, maxDynprogMs, datasetName);

                solutions.add(singleCCSolutions);
                logger.info("[{}/{}] CC solutions calculated.",i+1,ccCount);
            }
        } else {
            
            File vertices = new File("res/vertices and adjacencies/"+datasetName+"/vertecies.csv");
            File adjacencies = new File("res/vertices and adjacencies/"+datasetName+"/adjacencies.csv");

            
            AggregationSolver aggregationSolver = new AggregationSolver(vertices, adjacencies);
            MincutGraph mincutGraph = aggregationSolver.getMincutGraph();

            
            solutions = benchmarkGraph(mincutGraph, decomposerKind, decimals, uniqueWeights, mode,maxRootCount, ntdCount, onlyBiggestComponent, maxDynprogMs);

            
            aggregationSolver.transformSolution(solutions, mincutGraph.idOffset);

        }
        currentDatasetName = null;
    }

    
    public static List<List<Solution>> benchmarkGraph(MincutGraph graph, MincutSolver.DecomposerKind decomposerKind, int decimals,boolean uniqueWeights, BenchmarkMode mode,int maxRootCount, int ntdCount, boolean onlyBiggest, long maxDynprogMs) {
        List<List<Solution>> solutions = new ArrayList<>();

        
        List<MincutGraph> graphs = MincutSolver.getConnComps(graph);

        
        if (onlyBiggest)
            graphs = List.of(graphs.get(graphs.size() - 1));

        for (int i = 0; i < graphs.size(); i++) {
            MincutGraph singleGraph = graphs.get(i);
            logger.info("[{}/{}] Solving CC...",i+1,graphs.size());

            
            solutions.add(benchmarkSingleComponentMode(singleGraph, null,decomposerKind, mode,maxRootCount,ntdCount,decimals,uniqueWeights,maxDynprogMs, "unknown"));

            logger.info("[{}/{}] CC solutions calculated.",i+1,graphs.size());
        }
        return solutions;
    }

    public static List<Solution> benchmarkSingleComponentMode(MincutGraph singeComponentGraph, Ntd ntd, MincutSolver.DecomposerKind decomposerKind, BenchmarkMode mode, int maxRootCount, int ntdCount, int decimals, boolean uniqueWeights, long maxDynprogMs, String datasetName) {
        
        current_graph_id++;
        current_ntd_nr = 0;


        
        currentResult = new Result();
        currentResult.graph_id = current_graph_id;
        currentResult.graph_num_vertices = singeComponentGraph.getJd_graph().getNumVertices();
        currentResult.graph_num_edges = singeComponentGraph.getJd_graph().getNumberOfEdges();
        DataLog.graph(String.format("%d,%d,%d",
                currentResult.graph_id,
                currentResult.graph_num_vertices, currentResult.graph_num_edges));

        List<Solution> solutions = null;

        switch (mode) {
            case STANDARD -> {
                solutions = benchmarkSingleComponent(singeComponentGraph, ntd, decomposerKind, decimals, uniqueWeights,true, maxDynprogMs, datasetName);
            }
            case ALL_ROOTS -> {
                if (ntd == null) {
                    ntd = generateNtd(decomposerKind, singeComponentGraph);
                }

                
                long simStartTime = System.nanoTime();
                Simulator simulator = new Simulator(ntd);
                simulator.estimateAllRoots();
                long simEndTime = System.nanoTime();

                int better_root_time = (int) ((simEndTime - simStartTime) / 1_000_000.0);

                for (Map.Entry<BigInteger[], NtdNode> entry : simulator.getShortenedRootsMap(maxRootCount).entrySet()) {

                    currentResult = new Result();
                    currentResult.graph_id = current_graph_id;
                    currentResult.estimated_time = entry.getKey()[0];
                    currentResult.root_count = ntd.getNumberOfJoinNodes()+2;
                    currentResult.better_root_time = better_root_time;
                    Ntd newNtd = (NtdTransformer.copyNtd(ntd, entry.getValue()));
                    solutions = benchmarkSingleComponent(singeComponentGraph, newNtd, decomposerKind, decimals, uniqueWeights,false, maxDynprogMs, datasetName);
                    current_ntd_nr++;
                }
            }
            case MULTIPLE_NTD -> {
                for (int i = 0; i < ntdCount; i++) {
                    currentResult = new Result();
                    currentResult.graph_id = current_graph_id;
                    solutions = benchmarkSingleComponent(singeComponentGraph, null, decomposerKind, decimals, uniqueWeights,true, maxDynprogMs, datasetName);
                    current_ntd_nr++;
                }
            }
        }
        return solutions;
    }

    private static List<Solution> benchmarkSingleComponent(MincutGraph singeComponentGraph, Ntd ntd, MincutSolver.DecomposerKind decomposerKind, int decimals, boolean uniqueWeights, boolean betterRoot, long maxDynprogMs, String datasetName) {
        
        if (Settings.fuseIntroduceJoinForgetNodes) {
            if(!Settings.fuseJoinForgetNodes) {
                logger.warn("fuseIntroduceJoinForgetNodes can only be used in conjunction with fuseJoinForgetNodes. Activating fuseJoinForgetNodes...");
                Settings.fuseJoinForgetNodes = true;
            }
            if (!Settings.outsource) {
                logger.warn("fuseIntroduceJoinForgetNodes can only be used in conjunction with outsourcing. Activating outsourcing...");
                Settings.outsource = true;
            }
        }

        AtomicReference<List<Solution>> solutions = new AtomicReference<>();
        AtomicReference<Ntd> ntd_atomic = new AtomicReference<>(ntd);

        
        currentResult.ntd_nr = current_ntd_nr;

        
        if (ntd_atomic.get() == null) {
            ntd_atomic.set(generateNtd(decomposerKind, singeComponentGraph));
        }

        
        if (betterRoot) {
            long simStartTime = System.nanoTime();
            Simulator simulator = new Simulator(ntd_atomic.get());
            TreeMap<BigInteger[], NtdNode> estimatedTimeMap = simulator.estimateAllRoots();
            Map.Entry<BigInteger[], NtdNode> minEntry = estimatedTimeMap.firstEntry();
            NtdNode bestRoot = minEntry.getValue();
            ntd_atomic.set(NtdTransformer.copyNtd(ntd_atomic.get(), bestRoot));
            long simEndTime = System.nanoTime();
            currentResult.estimated_time = minEntry.getKey()[0];
            currentResult.estimated_origin_pointer_count = minEntry.getKey()[4].longValue();
            currentResult.root_count = ntd_atomic.get().getNumberOfJoinNodes()+2;
            currentResult.better_root_time = (int) ((simEndTime - simStartTime) / 1_000_000.0);
        }


        
        currentResult.ntd_tw = ntd_atomic.get().getTw();
        currentResult.ntd_num_nodes = ntd_atomic.get().getNumberOfNodes();
        currentResult.ntd_num_join_nodes = ntd_atomic.get().getNumberOfJoinNodes();
        currentResult.decomposer_kind = decomposerKind;
        DataLog.ntd(String.format("%d,%d,%d,%d,%d,%d,%s,%d,%d,%d,%d",
                currentResult.graph_id, currentResult.ntd_nr,
                currentResult.ntd_tw, currentResult.ntd_num_nodes, currentResult.ntd_num_join_nodes,
                currentResult.jd_total_time, currentResult.decomposer_kind.toString(), currentResult.better_root_time, currentResult.root_count,
                currentResult.jd_max_heap_usage, currentResult.ntd_max_cpu_diff));

        
        currentResult.decimals = decimals;
        currentResult.outsourced = Settings.outsource ? "yes" : "no";
        currentResult.uniqueWeights = uniqueWeights;

        DataLog.execution(String.format("%d,%d,%s,%d,%s,%s,%s,%d,%d,%s,%d,%d,",
                currentResult.graph_id, currentResult.ntd_nr,
                currentResult.outsourced,
                Settings.pruneFreeSpacePuffer,
                Settings.useJoinNodeHeuristic ? "yes" : "no",
                Settings.fuseJoinForgetNodes ? "yes" : "no",
                Settings.fuseIntroduceJoinForgetNodes ? "yes" : "no",
                Settings.threadCount,
                currentResult.decimals,
                currentResult.uniqueWeights ? "yes" : "no",
                currentResult.estimated_time,
                currentResult.estimated_origin_pointer_count));

        currentBenchmarkThread = new Thread(() -> {
            byte[] reserve = new byte[1024 * 1024 * 1024 / 4]; 
            try {
                

                
                RuntimeWatcher.resetStatistics();
                long startTime = System.nanoTime();
                currentResult.startTime = startTime;

                solutions.set(new MincutSolver(singeComponentGraph).solveSingleComponent(singeComponentGraph, ntd_atomic.get(), decimals, uniqueWeights));

                long endTime = System.nanoTime();

                
                currentResult.pareto_mincut_time = (int) ((endTime - startTime - currentResult.pareto_mincut_time_offset) / 1_000_000.0);
                currentResult.solution_count = solutions.get().size();
                currentResult.abort_reason = "none";
                System.gc();

                

            } catch (Throwable t) {
                
                reserve = new byte[1];

                
                if (currentResult.abort_reason.equals("TIMEOUT"))
                    return;

                
                if (t instanceof OutOfMemoryError || t.getCause() instanceof OutOfMemoryError) {
                    if (Settings.heapDumpOnOOME) {
                        Diagnostics.createHeapDump(Main.sessionOutputFolder+"/heapdumps/");
                    }

                    currentResult.abort_reason = "OOME";
                    logger.warn("OOME catched",t);

                    
                    RuntimeWatcher.start();
                    RuntimeWatcher.resetStatistics();
                    return;
                }

                
                logger.error("An exception occurred", t);
            }
        });
        currentBenchmarkThread.start();

        try {
            
            currentBenchmarkThread.join(maxDynprogMs);

            
            currentResult.pareto_mincut_max_cpu_diff = (int) RuntimeWatcher.getMaxCpuDiff();
            currentResult.pareto_mincut_max_heap_usage = RuntimeWatcher.getMaxHeapMiB();

            if (currentBenchmarkThread.isAlive()) {
                
                logger.info("Dynprog thread has exceeded the time limit of {} ms, stopping thread...", maxDynprogMs);
                currentResult.abort_reason = "TIMEOUT";
                currentBenchmarkThread.interrupt();
                long startTime = System.nanoTime();
                currentBenchmarkThread.join();
                long endTime = System.nanoTime();
                logger.info("Dynprog thread ended within {} ms.", (int) ((endTime - startTime - currentResult.pareto_mincut_time_offset) / 1_000_000.0));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        
        DataLog.execution(String.format("%d,%s,%d,%d,%d,%d\n",
                currentResult.pareto_mincut_time,
                currentResult.abort_reason,
                currentResult.solution_count,
                currentResult.pareto_mincut_max_heap_usage,
                currentResult.pareto_mincut_max_cpu_diff,
                RuntimeWatcher.getMaxOutsourceFolderMib()));

        
        if (Settings.sendMailOnExecution) {
            Mailer.sendMail("Execution durch", String.format("datasetName: %s\n tw: %d\npareto_mincut_time: %d\nabort_reason: %s\nsolution_count: %d",
                    datasetName,
                    ntd_atomic.get().getTw(),
                    currentResult.pareto_mincut_time,
                    currentResult.abort_reason,
                    currentResult.solution_count));
        }

        currentResult = null;


        if (solutions.get() != null) {
            return solutions.get();
        } else {
            
            return new ArrayList<>();

        }
    }


    public static void saveSolutions(List<Solution> solutions, String fileName, int idOffset) {
        
        File solutionFile = IO.getFreeFileName(fileName);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(solutionFile))) {
            if (idOffset != 0) {
                writer.write("Attention: Node ID offset of " + idOffset + " must still be added.\n");
            }

            
            for (Object solution : solutions) {
                writer.write(solution.toString());
                writer.newLine(); 
            }
        } catch (IOException e) {
            logger.warn("Solution could not be saved", e);
        }
    }
}
