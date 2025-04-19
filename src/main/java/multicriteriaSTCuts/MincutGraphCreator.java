package multicriteriaSTCuts;

import jdrasil.graph.invariants.ConnectedComponents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MincutGraphCreator {
    static private final Logger logger = LoggerFactory.getLogger(MincutGraphCreator.class);


    public static MincutGraph getRandomGraph(int weightDimension, int numVertices, double sEdgeProp, double uvEdgeProp, double tEdgeProp) {

        MincutGraph mincutGraph;
        Random rnd = new Random();
        logger.info("Random graph is generated...");
        while (true) {
            mincutGraph = new MincutGraph(weightDimension);

            for (int v = 0; v < numVertices; v++) {
                
                mincutGraph.getJd_graph().addVertex(v);

                
                if (rnd.nextDouble() < sEdgeProp) {
                    mincutGraph.increaseEdgeWeight("s",v,getUniformRandomIntWeight(weightDimension));
                }
                
                if (rnd.nextDouble() < tEdgeProp) {
                    mincutGraph.increaseEdgeWeight("t",v,getUniformRandomIntWeight(weightDimension));
                }

                
                for (int u = v+1; u < numVertices; u++) {
                    if (rnd.nextDouble() < uvEdgeProp) {
                        mincutGraph.increaseEdgeWeight(u,v,getUniformRandomIntWeight(weightDimension));
                    }
                }
            }

            
            Map<Integer, Set<Integer>> components = new ConnectedComponents<>(mincutGraph.getJd_graph()).getComponents();
            if (components.size() == 1) {
                logger.info("Random graph generation done.");
                return mincutGraph;
            }
        }
    }

    private static double[] getUniformRandomIntWeight(int weightDimension) {
        double[] weight = new double[weightDimension];
        Random rnd = new Random();
        for (int i = 0; i < weightDimension; i++) {
            weight[i] = rnd.nextInt(0, 1_000);
        }
        return weight;
    }
}
