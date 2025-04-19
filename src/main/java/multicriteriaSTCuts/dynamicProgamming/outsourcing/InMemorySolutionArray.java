package multicriteriaSTCuts.dynamicProgamming.outsourcing;

import datastructures.Ntd;
import datastructures.NtdNode;
import multicriteriaSTCuts.Solution;
import multicriteriaSTCuts.dynamicProgamming.SolutionPointer;

import java.util.*;

public class InMemorySolutionArray extends SolutionArray {

    private ArrayList<SolutionPointer>[] solutionArray;

    public InMemorySolutionArray(int size) {
        solutionArray = new ArrayList[size];
    }

    @Override
    public void set(int idx, ArrayList<SolutionPointer> entry) {
        solutionArray[idx] = entry;
    }

    @Override
    public ArrayList<SolutionPointer> get(int idx) {
        return solutionArray[idx];
    }

    @Override
    public int getEntrySize(int idx) {
        return solutionArray[idx].size();
    }

    @Override
    public int getEntryCount() {
        return (int) Arrays.stream(solutionArray).filter(Objects::nonNull).count();
    }

    @Override
    public List<Solution> getSolutions(Ntd ntd) {
        return reconstructSolutions(solutionArray[0],ntd);
    }

    @Override
    public Iterator<Integer> getDebugNonNullIndexIterator(boolean ascending, int vBinary) {
        return new Iterator<>() {
            private Integer currentIdx;

            {
                currentIdx = ascending ? -1 : solutionArray.length;
            }

            @Override
            public boolean hasNext() {
                return getNext(currentIdx) != null;
            }

            @Override
            public Integer next() {
                currentIdx = getNext(currentIdx);
                return currentIdx;
            }

            private Integer getNext(Integer idx) {
                if (ascending) {
                    idx++;
                    while (idx < solutionArray.length && (solutionArray[idx] == null || (idx & vBinary) != 0)) {
                        idx++;
                    }
                } else {
                    idx--;
                    while (idx >= 0 && (solutionArray[idx] == null || (idx & vBinary) != 0)) {
                        idx--;
                    }
                }

                if(idx < 0 || idx >= solutionArray.length)
                    idx = null;

                return idx;
            }
        };
    }
    private List<Solution> reconstructSolutions(List<SolutionPointer> solutionPointers, Ntd ntd) {
        List<Solution> solutions = new ArrayList<>(solutionPointers.size());

        
        ntd.createNodeIdMap();

        
        for (SolutionPointer solutionPointer : solutionPointers) {
            solutions.add(new Solution(solutionPointer,ntd));
        }
        return solutions;
    }
}
