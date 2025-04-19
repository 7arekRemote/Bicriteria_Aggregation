package multicriteriaSTCuts.dynamicProgamming.outsourcing;

import datastructures.Ntd;
import main.Settings;
import multicriteriaSTCuts.Solution;
import multicriteriaSTCuts.dynamicProgamming.MincutDynprog;
import multicriteriaSTCuts.dynamicProgamming.SolutionPointer;

import java.util.*;

public abstract class SolutionArray {

    public static SolutionArray createSolutionArray(int arraySize, int stackIdx, MincutDynprog dynprog) {
        if (Settings.outsource) {
            return new OutsourcedSolutionArray(stackIdx, dynprog);
        } else {
            return new InMemorySolutionArray(arraySize);
        }
    }

    public abstract void set(int idx, ArrayList<SolutionPointer> entry);
    public abstract ArrayList<SolutionPointer> get(int idx);
    public abstract int getEntrySize(int idx);
    public abstract int getEntryCount();

    public abstract List<Solution> getSolutions(Ntd ntd);

    public abstract Iterator<Integer> getDebugNonNullIndexIterator(boolean ascending, int vBinary);
    public Iterator<Integer> getNonNullIndexIterator(Collection<Integer> vertices, Map<Integer, Integer> pathIndex) {
        return new SubsetBitmaskIterator(vertices, pathIndex);
    }

    static class SubsetBitmaskIterator implements Iterator<Integer> {
        private final List<Integer> bagBinaries;
        private final int subsetCount;
        private int nextSubset;

        public SubsetBitmaskIterator(Collection<Integer> vertices, Map<Integer, Integer> pathIndex) {
            
            Set<Integer> verticesBinaries = new HashSet<>();
            for (Integer element : vertices) {
                verticesBinaries.add(pathIndex.get(element));
            }
            
            this.bagBinaries = new ArrayList<>(verticesBinaries);
            this.subsetCount = 1 << bagBinaries.size(); 
            this.nextSubset = 0; 
        }

        @Override
        public boolean hasNext() {
            return nextSubset < subsetCount;
        }

        @Override
        public Integer next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            int bitmask = 0;
            for (int i = 0; i < bagBinaries.size(); i++) {
                if ((nextSubset & (1 << i)) != 0) { 
                    bitmask |= (1 << bagBinaries.get(i)); 
                }
            }
            nextSubset++; 
            return bitmask;
        }
    }
}
