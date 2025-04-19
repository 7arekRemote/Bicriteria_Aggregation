package multicriteriaSTCuts;

import jdrasil.graph.Graph;
import jdrasil.graph.GraphFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.ArrayMath;

import java.util.HashMap;
import java.util.Map;


public class MincutGraph {
    static private final Logger logger = LoggerFactory.getLogger(MincutGraph.class);

    private final Graph<Integer> jd_graph;

    private final HashMap<Edge,double[]> edgeWeights;

    
    private final HashMap<Integer, double[]> sEdgesWeights;
    
    private final HashMap<Integer, double[]> tEdgesWeights;
    private final double[] stEdgeWeight;
    private final int weightDimension;

    public int idOffset;

    public MincutGraph(int weightDimension) {
        this.weightDimension = weightDimension;
        jd_graph = GraphFactory.emptyGraph();
        edgeWeights = new HashMap<>();
        sEdgesWeights = new HashMap<>();
        tEdgesWeights = new HashMap<>();
        stEdgeWeight = new double[weightDimension];
        idOffset = 0;
    }

    public MincutGraph(MincutGraph oMincutGraph, Graph<Integer> newJdGraph) {
        jd_graph = newJdGraph;
        edgeWeights = oMincutGraph.edgeWeights;
        sEdgesWeights = oMincutGraph.sEdgesWeights;
        tEdgesWeights = oMincutGraph.tEdgesWeights;
        weightDimension = oMincutGraph.weightDimension;
        stEdgeWeight = new double[weightDimension]; 
        idOffset = oMincutGraph.idOffset;
    }



    public void increaseEdgeWeight(String u, String v, double[] weight) {
        if ((u.equals("t") && v.equals("s")) || (u.equals("s") && v.equals("t"))) {
            ArrayMath.increaseArray(stEdgeWeight,weight);
        } else {
            throw new IllegalArgumentException();
        }
    }

    public void increaseEdgeWeight(String u, int v, double[] weight) {
        if (u.equals("s")) {
            if (!sEdgesWeights.containsKey(v)) {
                sEdgesWeights.put(v, weight);
                jd_graph.addVertex(v);
            } else {
                ArrayMath.increaseArray(sEdgesWeights.get(v), weight);
            }
        } else if (u.equals("t")) {
            if (!tEdgesWeights.containsKey(v)) {
                jd_graph.addVertex(v);
                tEdgesWeights.put(v, weight);
            } else {
                ArrayMath.increaseArray(tEdgesWeights.get(v), weight);
            }
        } else {
            throw new IllegalArgumentException();
        }
    }

    public void increaseEdgeWeight(int u, int v, double[] newWeight) {
        
        Edge newEdge = new Edge(u,v);

        if(edgeWeights.get(newEdge) == null) {
            
            jd_graph.addVertex(u);
            jd_graph.addVertex(v);
            jd_graph.addEdge(u, v);

            edgeWeights.put(newEdge, newWeight);
        } else {
            
            ArrayMath.increaseArray(edgeWeights.get(newEdge), newWeight);
        }
    }

    public double[] getEdgeWeight(String u, String v) {
        if ((u.equals("t") && v.equals("s")) || (u.equals("s") && v.equals("t"))) {
            return stEdgeWeight;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public double[] getEdgeWeight(String u, int v) {
        if (u.equals("s")) {
            return sEdgesWeights.get(v);
        } else if (u.equals("t")) {
            return tEdgesWeights.get(v);
        } else {
            throw new IllegalArgumentException();
        }
    }

    public double[] getEdgeWeight(int u, int v) {
        return edgeWeights.get(new Edge(u,v));
    }


    public Graph<Integer> getJd_graph() {
        return jd_graph;
    }

    public int getWeightDimension() {
        return weightDimension;
    }

    

    public void multiplyAllWeights(double multiplier) {
        
        ArrayMath.multiplyArray(stEdgeWeight, multiplier);

        
        for (double[] weight : sEdgesWeights.values()) {
            ArrayMath.multiplyArray(weight, multiplier);
        }

        
        for (double[] weight : tEdgesWeights.values()) {
            ArrayMath.multiplyArray(weight, multiplier);
        }

        
        for (double[] weight : edgeWeights.values()) {
            ArrayMath.multiplyArray(weight, multiplier);
        }
    }

    public void roundAllWeights() {
        
        ArrayMath.round(stEdgeWeight,0);

        
        for (double[] weight : sEdgesWeights.values()) {
            ArrayMath.round(weight,0);
        }

        
        for (double[] weight : tEdgesWeights.values()) {
            ArrayMath.round(weight,0);
        }

        
        for (Map.Entry<Edge,double[]> entry : edgeWeights.entrySet()) {
            ArrayMath.round(entry.getValue(),0);
        }

    }
    private static class Edge {
        int u;
        int v;

        public Edge(int u, int v) {
            this.u = Math.min(u,v);
            this.v = Math.max(u,v);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Edge edge = (Edge) o;

            return (u == edge.u && v == edge.v);
        }

        @Override
        public int hashCode() {
            return u ^ Integer.reverse(v);
        }
    }


}
