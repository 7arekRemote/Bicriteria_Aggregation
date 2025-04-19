package multicriteriaSTCuts.benchmark;

import multicriteriaSTCuts.MincutSolver;

import java.math.BigInteger;

public class Result {

    
    public long pareto_mincut_time_offset = 0; 

    
    public int graph_id = -1;
    public int ntd_nr = -1;
    
    
    public BigInteger estimated_time = BigInteger.valueOf(-1); 
    public int pareto_mincut_time = -1;
    public int solution_count = -1;
    public int pareto_mincut_max_heap_usage = -1;
    public String abort_reason = "unknown";
    public String outsourced = "unknown";
    public int pareto_mincut_max_cpu_diff = 0;
    public boolean uniqueWeights;
    public int decimals = -1;

    
    public int graph_num_vertices = -1;
    public int graph_num_edges = -1;

    
    public int ntd_tw = -1;
    public int ntd_num_nodes = -1;
    public int ntd_num_join_nodes = -1;
    public int jd_total_time = -1;
    public int better_root_time = -1;
    public int root_count = -1;
    public int ntd_max_cpu_diff = 0;
    public int jd_max_heap_usage = -1;
    public MincutSolver.DecomposerKind decomposer_kind;

    public long startTime = -1; 
    public long estimated_origin_pointer_count =-1;
}
