package dataLogging;

import main.Main;
import main.Settings;
import multicriteriaSTCuts.benchmark.Benchmark;
import multicriteriaSTCuts.dynamicProgamming.SolutionPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.IO;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import static utils.IO.getFreeFileName;

public class DataLog {
    static private final Logger logger = LoggerFactory.getLogger(DataLog.class);
    private static BufferedWriter graphLogger;
    private static BufferedWriter ntdLogger;
    private static BufferedWriter nodeLogger;
    private static BufferedWriter executionLogger;
    private static BufferedWriter spaceLogger;
    private static BufferedWriter heuristicNodeLogger;
    private static BufferedWriter pruneLogger;

    private static File joinDetailedFolder;
    public static File currentJdDatasetFolder;


    public static void init() throws IOException {
        if (Settings.dataLog) {
            logger.info("Initialize DataLogs...");
            
            String dataLogFolder = Main.sessionOutputFolder + "/dataLogs";
            new File(dataLogFolder).mkdirs();

            joinDetailedFolder = new File(dataLogFolder + "/joinDetailed");

            
            graphLogger = new BufferedWriter(new FileWriter(getFreeFileName(dataLogFolder + "/graphs.csv")));
            ntdLogger = new BufferedWriter(new FileWriter(getFreeFileName(dataLogFolder + "/ntds.csv")));
            nodeLogger = new BufferedWriter(new FileWriter(getFreeFileName(dataLogFolder + "/nodes.csv")));
            executionLogger = new BufferedWriter(new FileWriter(getFreeFileName(dataLogFolder + "/executions.csv")));
            spaceLogger = new BufferedWriter(new FileWriter(getFreeFileName(dataLogFolder + "/space.csv")));
            heuristicNodeLogger = new BufferedWriter(new FileWriter(getFreeFileName(dataLogFolder + "/heuristicNode.csv")));
            pruneLogger = new BufferedWriter(new FileWriter(getFreeFileName(dataLogFolder + "/prune.csv")));

            
            execution("graph_id,ntd_nr," +
                    "outsourced,prune_space_puffer," +
                    "used_join_heuristic," +
                    "fused_join_forget," +
                    "fused_introduce_join_forget," +
                    "threads,decimals,uniqueWeights," +
                    "estimated_time," +
                    "estimated_origin_pointer_count," +
                    "pareto_mincut_time,abort_reason,solution_count,pareto_mincut_max_heap_usage,pareto_mincut_max_cpu_diff,max_outsource_folder_mib\n");
            graph("graph_id,graph_num_vertices,graph_num_edges");
            ntd("graph_id,ntd_nr,ntd_tw,ntd_num_nodes,ntd_num_join_nodes,jd_total_time,decomposer_kind,better_root_time,root_count,jd_max_heap_usage,jd_max_cpu_diff");
            node("graph_id,ntd_nr," +
                    "node_nr," +
                    "bag_size," +
                    "first_ij_size," +
                    "second_ij_size," +
                    "fj_size," +
                    "vi,fi," +
                    "f_node_type,s_node_type,i_node_type," +
                    "f_solution_count,s_solution_count,i_solution_count," +
                    "maxHeapPercentage,node_time," +
                    "first_free_pointer_id,elapsed_ms");
            space("graph_id,ntd_nr," +
                    "elapsed_ms," +
                    "heap_MiB," +
                    "outsource_folder_MiB,stack_folder_MiB,origin_pointer_file_MiB," +
                    "outsource_folder_num_files,outsource_folder_measure_ms," +
                    "first_free_pointer_id");

            heuristicNode("graph_id,node_nr,entry_nr,fused_entry_nr," +
                    "recursion_level," +
                    "usedHeuristic," +
                    "pointCombs_skipped_percent,heapNodes_completely_skipped_percent,lineCombs_skipped_percent,poCalcSize_percent," +
                    "num_heapify," +
                    "aSize,bSize," +
                    "newSize");

            prune("graph_id,ntd_nr," +
                    "prune_start_elapsed_ms," +
                    "outsource_folder_MiB,stack_folder_MiB,origin_pointer_file_MiB," +
                    "stack_diff_sum_MiB," +
                    "free_space_MiB," +
                    "estimated_growth," +
                    "estimated_stack_growth," +
                    "estimated_op_growth," +
                    "pruned," +
                    "pre_prune_origin_lines,post_prune_origin_lines,abs_origin_diff,rel_origin_diff," +
                    "prune_ms",true);


            logger.info("Initialize DataLogs done.");
        }
    }

    public static void initJdDatasetFolder() {
        currentJdDatasetFolder = IO.getFreeFolderName(joinDetailedFolder + "/" + Benchmark.currentDatasetName);
    }

    public static void flush() {
        try {
            if (Settings.dataLog) {
                graphLogger.flush();
                ntdLogger.flush();
                nodeLogger.flush();
                executionLogger.flush();
                spaceLogger.flush();
                heuristicNodeLogger.flush();
                pruneLogger.flush();
            }
        } catch (IOException e) {
            logger.error("DataLogs could not be flushed.",e);
        }
    }

    public static void close() {
        try {
            if (Settings.dataLog) {
                logger.info("Closing DataLogs...");
                graphLogger.close();
                ntdLogger.close();
                nodeLogger.close();
                executionLogger.close();
                spaceLogger.close();
                heuristicNodeLogger.close();
                pruneLogger.close();
                logger.info("Closing DataLogs done.");
            }
        } catch (IOException e) {
            logger.error("DataLogs could not be closed.",e);
        }
    }

    public static void graph(String line) {
        if(!Settings.dataLog) return;
        try {
            graphLogger.write(line + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void ntd(String line) {
        if(!Settings.dataLog) return;
        try {
            ntdLogger.write(line + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void node(String line) {
        if(!Settings.dataLog) return;
        try {
            nodeLogger.write(line + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void execution(String line) {
        if(!Settings.dataLog) return;
        try {
            executionLogger.write(line);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void space(String line) {
        if(!Settings.dataLog) return;
        try {
            spaceLogger.write(line + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void heuristicNode(String line) {
        synchronized (heuristicNodeLogger) {
            if(!Settings.dataLog || !Settings.dataLogHeuristic) return;
            try {
                heuristicNodeLogger.write(line + "\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public static void prune(String line, boolean newLine) {
        if(!Settings.dataLog) return;
        try {
            if(newLine) pruneLogger.write(line + "\n");
            else pruneLogger.write(line);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
