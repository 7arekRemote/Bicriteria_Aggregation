package multicriteriaSTCuts.dynamicProgamming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.ArrayMath;

import java.util.Arrays;

public class SolutionPointer {
    static private final Logger logger = LoggerFactory.getLogger(SolutionPointer.class);
    public double[] weight;
    private int vertex; 
    public SolutionPointer solutionOrigin;
    public SolutionPointer secondSolutionOrigin; 

    public long id = -1; 

    public int joinNodeId = -1; 
    public int ivMask = 0; 
    public boolean isFromFirstChild = true; 

    private SolutionPointer() {
    }

    SolutionPointer(int weightDimension) {
        weight = new double[weightDimension];
        vertex = -1;
    }

    public SolutionPointer(double[] weight, Integer vertex, SolutionPointer solutionOrigin, SolutionPointer secondSolutionOrigin) {
        this.weight = weight;
        if (vertex == null)
            this.vertex = -1;
        else
            this.vertex = vertex;
        this.solutionOrigin = solutionOrigin;
        this.secondSolutionOrigin = secondSolutionOrigin;
    }

    public SolutionPointer(double[] weight, long id) {
        this.weight = weight;

        this.id = id;

    }

    public double[] getWeight() {
        return weight;
    }

    public void increaseWeight(double[] increment) {
        ArrayMath.increaseArray(this.weight, increment);
    }

    public Integer getVertex() {
        if(vertex == -1)
            return null;
        else
            return vertex;
    }

    public SolutionPointer getSolutionOrigin() {
        return solutionOrigin;
    }

    public SolutionPointer getSecondSolutionOrigin() {
        return secondSolutionOrigin;
    }

    @Override
    public String toString() {
        return Arrays.toString(weight);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {

        this.id = id;
    }

    public void setSolutionOrigin(SolutionPointer solutionOrigin) {
        this.solutionOrigin = solutionOrigin;
    }

    public void setSecondSolutionOrigin(SolutionPointer secondSolutionOrigin) {
        this.secondSolutionOrigin = secondSolutionOrigin;
    }
}
