package multicriteriaSTCuts.rootChoosing;


import ch.qos.logback.classic.Logger;
import datastructures.Ntd;
import datastructures.NtdNode;
import main.Settings;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;

public class Simulator {

    private static Logger logger = (Logger) LoggerFactory.getLogger(Simulator.class);

    private Ntd ntd;
    private ArrayList<NtdNode> potentialRoots;

    private BigInteger introduce_timeSum;
    private BigInteger forget_timeSum;
    private BigInteger join_timeSum;

    private TreeMap<BigInteger[], NtdNode> allRootsMap;

    private TreeMap<BigInteger[], NtdNode> shortenedRootsMap;

    
    private int join_checksum;
    private int node_checksum;


    HashMap<NtdNode, NtdNode> parentNode = new HashMap<>();

    public Simulator(Ntd ntd) {
        this.ntd = ntd;
        init();
    }

    public static Map.Entry<NtdNode, BigInteger> findMin(HashMap<NtdNode, BigInteger> map) {
        Map.Entry<NtdNode, BigInteger> min = new AbstractMap.SimpleEntry<>(null, null);
        for (Map.Entry<NtdNode, BigInteger> entry : map.entrySet()) {
            if(min.getValue() == null || entry.getValue().compareTo(min.getValue()) < 0){
                min = entry;
            }
        }
        return min;
    }

    public TreeMap<BigInteger[], NtdNode> estimateAllRoots() {

        HashMap<BigInteger[],NtdNode> inputMap = new HashMap<>();


        for (NtdNode potentialRoot : potentialRoots) {
            BigInteger[] currentTimes = getEstimatedTime(potentialRoot);
            if (join_checksum != 0 || node_checksum != 0) {
                logger.error("join_checksum: " + join_checksum + ", node_checksum: " + node_checksum);
                throw new RuntimeException("join checksum != 0 || node_checksum != 0");
            }
            inputMap.put(currentTimes, potentialRoot);
        }

        double wSpace;
        double wTime;

        if (Settings.ntdOptimizeEfficiencyScore) {
            wSpace = 3.0;
            wTime = 1.0;
        } else {
            wSpace = 0.0;
            wTime = 1.0;
        }


        BigInteger minTime = inputMap.keySet().stream()
                .map(key -> key[0])
                .min(Comparator.naturalOrder())
                .orElse(BigInteger.ONE); 
        BigInteger minSpace = inputMap.keySet().stream()
                .map(key -> key[4])
                .min(Comparator.naturalOrder())
                .orElse(BigInteger.ONE); 


        Comparator<BigInteger[]> efficiencyComparator = (arr1, arr2) -> {
            double normalizedTime1 = arr1[0].doubleValue() / minTime.doubleValue();
            double normalizedSpace1 = arr1[4].doubleValue() / minSpace.doubleValue();
            double efficiency1 = wTime * normalizedTime1 + wSpace * normalizedSpace1;

            double normalizedTime2 = arr2[0].doubleValue() / minTime.doubleValue();
            double normalizedSpace2 = arr2[4].doubleValue() / minSpace.doubleValue();
            double efficiency2 = wTime * normalizedTime2 + wSpace * normalizedSpace2;


            return Double.compare(efficiency1, efficiency2);
        };

        allRootsMap = new TreeMap<>(efficiencyComparator);

        allRootsMap.putAll(inputMap);









        if (Settings.forceMedianRoot) {
            List<Map.Entry<BigInteger[], NtdNode>> sortedEntries = new ArrayList<>(allRootsMap.entrySet());
            allRootsMap = new TreeMap<>(efficiencyComparator);

            Map.Entry<BigInteger[], NtdNode> entry = sortedEntries.get((int) Math.round(0.5d * (sortedEntries.size() - 1)));
            allRootsMap.put(entry.getKey(), entry.getValue());
        }

        return allRootsMap;
    }

    public BigInteger[] getEstimatedTime(NtdNode potentialRoot) {

        introduce_timeSum = BigInteger.valueOf(0);
        forget_timeSum = BigInteger.valueOf(0);
        join_timeSum = BigInteger.valueOf(0);
        join_checksum = ntd.getNumberOfJoinNodes();
        node_checksum = ntd.getNumberOfNodes();

        SimulatorVector resultVector;

        if (potentialRoot.getNodeType() == NtdNode.NodeType.LEAF) {
            resultVector = recursiveUpwardsSimulator(potentialRoot, null);
        } else if (potentialRoot.getNodeType() == NtdNode.NodeType.FORGET && potentialRoot.getBag().isEmpty()) {
            resultVector = recursiveDownwardsSimulator(potentialRoot);
        } else {
            
            throw new RuntimeException("getEstimatedTime: potential Root is neither LEAF nor original ROOT");
        }
        BigInteger total_timeSum = introduce_timeSum.add(forget_timeSum).add(join_timeSum);
        return new BigInteger[]{total_timeSum, join_timeSum, forget_timeSum, introduce_timeSum, BigInteger.valueOf(resultVector.originPointerCount)};
    }

    private SimulatorVector recursiveUpwardsSimulator(NtdNode currentNode, NtdNode predecessorNode) {
        node_checksum--;
        switch (currentNode.getNodeType()) {
            case LEAF -> {

                

                return recursiveUpwardsSimulator(parentNode.get(currentNode), currentNode);
            }
            case INTRODUCE -> {
                

                if(!Settings.fuseJoinForgetNodes || Settings.forceNoJfSim) {
                    
                    SimulatorVector first = recursiveUpwardsSimulator(parentNode.get(currentNode), currentNode);
                    handleForget(first, currentNode.getBag().size() - 1);
                    return first;
                }

                
                int fj_size = 0;
                NtdNode iterNode = currentNode;
                NtdNode iterPredecessorNode = currentNode;
                while (true) {
                    if (iterNode.getNodeType() == NtdNode.NodeType.INTRODUCE) {
                        
                        fj_size++;
                        iterPredecessorNode = iterNode;
                        iterNode = parentNode.get(iterNode);
                    } else if (iterNode.getNodeType() == NtdNode.NodeType.JOIN) {
                        


                        

                        
                        NtdNode otherChild = iterNode.getFirstChild() == iterPredecessorNode ? iterNode.getSecondChild() : iterNode.getFirstChild();

                        ij_skip firstSkip = skipIjNodesDownwards(otherChild);
                        SimulatorVector first = recursiveDownwardsSimulator(firstSkip.nodeAfterSkip());

                        
                        ij_skip secondSkip = skipIjNodesUpwards(parentNode.get(iterNode),iterNode);
                        SimulatorVector second = recursiveUpwardsSimulator(secondSkip.nodeAfterSkip(),secondSkip.predecessorNode());


                        
                        node_checksum-=fj_size; 
                        handleJoinForget(first, second, iterNode, fj_size, firstSkip.ij_size(), secondSkip.ij_size());

                        return first;
                    } else {
                        
                        
                        SimulatorVector first = recursiveUpwardsSimulator(parentNode.get(currentNode), currentNode);
                        handleForget(first, currentNode.getBag().size() - 1);
                        return first;
                    }
                }
            }
            case FORGET -> {
                SimulatorVector first;
                if (currentNode.getBag().isEmpty()) {
                    
                    first = handleLeaf();

                } else {
                    first = recursiveUpwardsSimulator(parentNode.get(currentNode), null);
                }

                
                handleIntroduce(first,null);

                return first;
            }
            case JOIN -> {
                
                NtdNode otherChild = currentNode.getFirstChild() == predecessorNode ? currentNode.getSecondChild() : currentNode.getFirstChild();
                SimulatorVector first = recursiveDownwardsSimulator(otherChild);
                
                SimulatorVector second = recursiveUpwardsSimulator(parentNode.get(currentNode), currentNode);

                
                handleJoin(first, second, currentNode);

                return first;
            }

        }
        throw new RuntimeException("recursiveUpwardsSimulator: Node type unknown");
    }


    private ij_skip skipIjNodesUpwards(NtdNode node,NtdNode predecessorNode) {
        NtdNode iterNode = node;
        int ij_size = 0;
        while (true) {
            if (iterNode.getNodeType() == NtdNode.NodeType.FORGET) {
                ij_size++;
                if (iterNode.getBag().isEmpty()) {
                    
                    return new ij_skip(iterNode, ij_size,predecessorNode);
                }
                node_checksum--;
                predecessorNode = iterNode;
                iterNode = parentNode.get(iterNode);
            } else {
                return new ij_skip(iterNode, ij_size,predecessorNode);
            }
        }
    }

    private SimulatorVector recursiveDownwardsSimulator(NtdNode currentNode) {
        node_checksum--;
        switch (currentNode.getNodeType()) {
            case LEAF -> {
                return handleLeaf();
            }
            case INTRODUCE -> {
                SimulatorVector first = recursiveDownwardsSimulator(currentNode.getFirstChild());
                handleIntroduce(first, currentNode);
                return first;
            }
            case FORGET -> {

                if (!Settings.fuseJoinForgetNodes || Settings.forceNoJfSim) {
                    
                    SimulatorVector first = recursiveDownwardsSimulator(currentNode.getFirstChild());
                    handleForget(first, currentNode.getBag().size());
                    return first;
                }

                
                int fj_size = 0;
                NtdNode iterNode = currentNode;
                while (true) {
                    if (iterNode.getNodeType() == NtdNode.NodeType.FORGET) {
                        
                        fj_size++;
                        iterNode = iterNode.getFirstChild();
                    } else if (iterNode.getNodeType() == NtdNode.NodeType.JOIN) {

                        

                        
                        ij_skip firstSkip = skipIjNodesDownwards(iterNode.getFirstChild());
                        ij_skip secondSkip = skipIjNodesDownwards(iterNode.getSecondChild());

                        SimulatorVector first = recursiveDownwardsSimulator(firstSkip.nodeAfterSkip());
                        SimulatorVector second = recursiveDownwardsSimulator(secondSkip.nodeAfterSkip());


                        node_checksum-=fj_size; 
                        handleJoinForget(first, second, iterNode, fj_size, firstSkip.ij_size(), secondSkip.ij_size());
                        return first;
                    } else {
                        
                        
                        SimulatorVector first = recursiveDownwardsSimulator(currentNode.getFirstChild());
                        handleForget(first, currentNode.getBag().size());
                        return first;
                    }
                }
            }
            case JOIN -> {
                SimulatorVector first = recursiveDownwardsSimulator(currentNode.getFirstChild());
                SimulatorVector second = recursiveDownwardsSimulator(currentNode.getSecondChild());
                handleJoin(first, second, currentNode);
                return first;
            }
        }
        throw new RuntimeException("recursiveDownwardsSimulator: Node type unknown");
    }

    private ij_skip skipIjNodesDownwards(NtdNode node) {
        NtdNode iterNode = node;
        int ij_size = 0;
        while (true) {
            if (iterNode.getNodeType() == NtdNode.NodeType.INTRODUCE) {
                ij_size++;
                node_checksum--;
                iterNode = iterNode.getFirstChild();
            } else {
                return new ij_skip(iterNode, ij_size,null);
            }
        }
    }


    private double func(double x, double exponent, double coefficient, double intercept, Double MIN, Double MAX) {
        double value = Math.pow(x, exponent) * coefficient + intercept;
        if (MIN != null) {
            value = Math.max(value, MIN);
        }
        if (MAX != null) {
            value = Math.min(value, MAX);
        }
        return value;
    }

    private BigInteger bigFunc(double x,double exponent, double coefficient, double intercept, Double MIN, Double MAX) {
        return  BigInteger.valueOf((long) func(x, exponent,  coefficient,  intercept, MIN, MAX));
    }

    private SimulatorVector handleLeaf() {
        return new SimulatorVector(1,1);
    }

    private void handleIntroduce(SimulatorVector first, NtdNode node) {
        


        
        first.originPointerCount += (long) first.solution_count;

        
        first.solution_count = func(first.solution_count, 1, 2, 0, null, null);
    }

    private void handleForget(SimulatorVector first, int bagSize) {
        


        
        int T = (int) Math.pow(2, bagSize+1);
        double merge_length = first.solution_count / T * 2;

        first.solution_count = func(merge_length, 0.99894672, 0.50483359, 0, null, null);
        first.solution_count *= (double) T /2;
    }

    private void handleJoin(SimulatorVector first, SimulatorVector second, NtdNode node) {
        join_checksum--;

        double bigger = Math.max(first.solution_count, second.solution_count);
        double smaller = Math.min(first.solution_count, second.solution_count);
        double ratio = smaller / bigger;

        
        int T = (int) Math.pow(2, node.getBag().size());
        long estimated_time = (long) (bigger*(smaller/(double) T)*Math.log(smaller/T)*3.8165337598323774e-08); 
        join_timeSum = join_timeSum.add(BigInteger.valueOf(estimated_time));

        
        first.solution_count = bigger * (1+Math.pow(ratio,0.5719459198844166)*2.147249513260121);
        first.solution_count = Math.max(first.solution_count, 1); 

        
        first.originPointerCount = first.originPointerCount + second.originPointerCount + (long) first.solution_count;

    }

    private void handleJoinForget(SimulatorVector first, SimulatorVector second, NtdNode node, int fjSize, int first_ij_size, int second_ij_size) {
        join_checksum--;

        
        first.solution_count *= Math.pow(2, first_ij_size);
        second.solution_count *= Math.pow(2, second_ij_size);


        double bigger = Math.max(first.solution_count, second.solution_count);
        double smaller = Math.min(first.solution_count, second.solution_count);
        double ratio = smaller / bigger;

        
        int T = (int) Math.pow(2, node.getBag().size());
        long estimated_time = (long) (bigger*(smaller/(double) T)*Math.log(smaller/T)*3.970148560516592e-08 * Math.pow(0.50002359083,(fjSize-1))); 
        join_timeSum = join_timeSum.add(BigInteger.valueOf(estimated_time));

        
        first.solution_count = bigger * (1+Math.pow(ratio,0.5719459198844166)*2.147249513260121);
        first.solution_count = Math.max(first.solution_count, 1); 

        
        for (int i = 0; i < fjSize; i++) {
            handleForget(first,node.getBag().size()-(i+1));
        }

        
        first.originPointerCount = first.originPointerCount + second.originPointerCount + (long) first.solution_count;
    }


    
    private void init() {
        potentialRoots = new ArrayList<>();

        Stack<NtdNode> nodeStack = new Stack<>();

        potentialRoots.add(ntd.getRoot());
        nodeStack.push(ntd.getRoot());
        parentNode.put(ntd.getRoot().getFirstChild(), ntd.getRoot());

        while (!nodeStack.isEmpty()) {
            NtdNode currentNode = nodeStack.pop();

            if (currentNode.getNodeType() == NtdNode.NodeType.LEAF) {
                potentialRoots.add(currentNode);
            } else if (currentNode.getNodeType() == NtdNode.NodeType.JOIN) {
                nodeStack.push(currentNode.getFirstChild());
                nodeStack.push(currentNode.getSecondChild());
                parentNode.put(currentNode.getFirstChild(), currentNode);
                parentNode.put(currentNode.getSecondChild(), currentNode);
            } else {
                nodeStack.push(currentNode.getFirstChild());
                parentNode.put(currentNode.getFirstChild(), currentNode);
            }
        }
    }



    public TreeMap<BigInteger[], NtdNode> getAllRootsMap() {
        return allRootsMap;
    }

    public TreeMap<BigInteger[], NtdNode> getShortenedRootsMap(int maxRootCount) {
        shortenedRootsMap = new TreeMap<>(Comparator.comparing(o -> o[0]));


        List<Map.Entry<BigInteger[], NtdNode>> sortedEntries = new ArrayList<>(allRootsMap.entrySet());


        for (int i = 0; i <= 100; i += (100 / (maxRootCount - 1))) {
            int index = (int) Math.round(i / 100.0 * (sortedEntries.size() - 1));

            Map.Entry<BigInteger[], NtdNode> entry = sortedEntries.get(index);
            shortenedRootsMap.put(entry.getKey(), entry.getValue());
        }

        logger.debug(String.format("Estimated runtime per root (percentile sampling to %d roots)", maxRootCount));
        logger.debug("total_timeSum, join_timeSum, forget_timeSum, introduce_timeSum");
        for (Map.Entry<BigInteger[], NtdNode> entry : shortenedRootsMap.entrySet()) {
            logger.debug(String.format("%d, %d, %d, %d", entry.getKey()[0], entry.getKey()[1], entry.getKey()[2], entry.getKey()[3]));
        }
        return shortenedRootsMap;
    }
}

record ij_skip(NtdNode nodeAfterSkip, int ij_size, NtdNode predecessorNode){}


class SimulatorVector {
    double solution_count;
    long originPointerCount;

    public SimulatorVector(double solution_count, long originPointerCount) {
        this.solution_count = solution_count;
        this.originPointerCount = originPointerCount;
    }
}
