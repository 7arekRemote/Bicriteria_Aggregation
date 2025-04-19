package bicriteriaAggregation;

import multicriteriaSTCuts.MincutGraph;
import multicriteriaSTCuts.MincutGraphIO;
import multicriteriaSTCuts.MincutSolver;
import multicriteriaSTCuts.Solution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class AggregationSolver {

    private final Logger logger = LoggerFactory.getLogger(AggregationSolver.class);

    File verticesFile;
    File adjacenciesFile;

    public AggregationSolver(File verticesFile, File adjacenciesFile) {
        this.verticesFile = verticesFile;
        this.adjacenciesFile = adjacenciesFile;
    }

    public List<List<Solution>> solve(MincutSolver.DecomposerKind decomposerKind,boolean uniqueWeights) {

        
        MincutGraph mincutGraph = getMincutGraph();

        
        MincutSolver mincutSolver = new MincutSolver(mincutGraph);
        List<List<Solution>> mincutSolutions = mincutSolver.solve(decomposerKind,uniqueWeights);

        
        transformSolution(mincutSolutions,mincutGraph.idOffset);

        return mincutSolutions;
    }

    public MincutGraph getMincutGraph() {
        logger.info("MincutGraph is generated...");
        MincutGraph mincutGraph = MincutGraphIO.readGraphFromDataset(verticesFile, adjacenciesFile);
        logger.info("MincutGraph generation finished.");
        return mincutGraph;
    }


    public void transformSolution(List<List<Solution>> mincutSolutions, int idOffset) {
        
        for (List<Solution> solutionList : mincutSolutions) {
            for (Solution solution : solutionList) {
                ArrayList<Integer> newVertexSet = new ArrayList<>();
                for (Integer vertex : solution.getVertices()) {
                    newVertexSet.add(vertex + idOffset);
                }
                solution.setVertices(newVertexSet);
            }
        }
    }
}
