package datastructures;

import jdrasil.algorithms.postprocessing.NiceTreeDecomposition;
import jdrasil.graph.Bag;
import jdrasil.graph.TreeDecomposition;
import main.Main;
import main.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static utils.IO.getFreeFileName;
import static datastructures.NtdNode.NodeType.*;

public class NtdTransformer {

    private final static Logger logger = LoggerFactory.getLogger(NtdTransformer.class);

    public static void fuseJoinForgetNodes(Ntd ntd) {
        Set<NtdNode> visited = new HashSet<>();
        Stack<NtdNode> stack = new Stack<>();
        stack.push(ntd.getRoot());

        NtdNode currentJoinNode = null;
        NtdNode lastForgetNode = null;
        Set<Integer> forgottenVertices = new HashSet<>();

        
        while (!stack.isEmpty()) {
            NtdNode currentNode = stack.peek();
            if (visited.contains(currentNode)) {
                stack.pop();

                if(currentJoinNode != null){
                    if (currentNode.nodeType != FORGET) {
                        
                        if (!forgottenVertices.isEmpty()) {
                            
                            

                            NtdNode joinForgetNode = fuseJoinAndForget(ntd, currentJoinNode, forgottenVertices);
                            
                            if(currentNode.firstChild == lastForgetNode){
                                currentNode.firstChild = joinForgetNode;
                            } else {
                                currentNode.secondChild = joinForgetNode;
                            }
                        }
                        
                        currentJoinNode = null;
                        forgottenVertices.clear();
                        lastForgetNode = null;
                    } else {
                        
                        forgottenVertices.add(currentNode.specialVertex);
                        lastForgetNode = currentNode;
                    }
                }

                
                if (currentNode.nodeType == JOIN) {
                    currentJoinNode = currentNode;
                }

                continue;
            }
            visited.add(currentNode);
            if (currentNode.getFirstChild() != null) {
                stack.push(currentNode.getFirstChild());
                if (currentNode.getSecondChild() != null)
                    stack.push(currentNode.getSecondChild());
            }
        }

        
        if(currentJoinNode != null && !forgottenVertices.isEmpty()){
            ntd.root = fuseJoinAndForget(ntd, currentJoinNode, forgottenVertices);
        }
    }

    private static NtdNode fuseJoinAndForget(Ntd ntd, NtdNode currentJoinNode, Set<Integer> forgottenVertices) {
        NtdNode joinForgetNode = new NtdNode(currentJoinNode.id);
        joinForgetNode.firstChild = currentJoinNode.firstChild;
        joinForgetNode.secondChild = currentJoinNode.secondChild;
        joinForgetNode.bag = new HashSet<>(currentJoinNode.bag);
        joinForgetNode.nodeType = JOIN_FORGET;
        joinForgetNode.forgottenVertices = new HashSet<>(forgottenVertices);

        ntd.getPathIndicesMap().put(joinForgetNode, ntd.getPathIndicesMap().get(currentJoinNode));
        ntd.getPathMaxBagSize().put(joinForgetNode, ntd.getPathMaxBagSize().get(currentJoinNode));
        return joinForgetNode;
    }

    public static Ntd getNtdFromJdNtd(NiceTreeDecomposition<Integer> jd_ntd){
        Ntd ntd = new Ntd();

        
        TreeDecomposition<Integer> jd_td = jd_ntd.getProcessedTreeDecomposition();

        
        int numberOfBags = jd_td.getNumberOfBags();

        ntd.tw = jd_td.getWidth();
        ntd.numberOfNodes = numberOfBags;
        ntd.numberOfJoinNodes = 0; 

        
        ArrayList<NtdNode> nodes = new ArrayList<>();
        for (int i = 0; i < numberOfBags; i++) {
            nodes.add(new NtdNode(i+1));
        }

        
        for (Bag<Integer> bag : jd_td.getTree()) {
            nodes.get(bag.id - 1).bag = new HashSet<>(bag.vertices);
        }

        
        ntd.root = nodes.get(jd_ntd.getRoot().id - 1);

        
        Stack<Bag<Integer>> jd_bag_stack = new Stack<>();
        Set<Bag<Integer>> jd_bags_visited = new HashSet<>();
        jd_bag_stack.push(jd_ntd.getRoot());

        int checkSum = 0; 

        while (!jd_bag_stack.empty()) {
            Bag<Integer> jd_currentBag = jd_bag_stack.pop();
            jd_bags_visited.add(jd_currentBag);
            ArrayList<Bag<Integer>> jd_currentNeighbourhood = new ArrayList<>(jd_td.getNeighborhood(jd_currentBag));
            jd_currentNeighbourhood.removeAll(jd_bags_visited);

            NtdNode currentNode = nodes.get(jd_currentBag.id - 1);

            switch (jd_ntd.bagType.get(jd_currentBag)) {
                case LEAF:
                    currentNode.nodeType = LEAF;
                    checkSum++;
                    break;
                case INTRODUCE:
                    currentNode.nodeType = INTRODUCE;
                    currentNode.firstChild = nodes.get(jd_currentNeighbourhood.get(0).id - 1);
                    jd_bag_stack.push(jd_currentNeighbourhood.get(0));
                    currentNode.specialVertex = jd_ntd.specialVertex.get(jd_currentBag);
                    checkSum++;
                    break;
                case FORGET:
                    currentNode.nodeType = NtdNode.NodeType.FORGET;
                    currentNode.firstChild = nodes.get(jd_currentNeighbourhood.get(0).id - 1);
                    jd_bag_stack.push(jd_currentNeighbourhood.get(0));
                    currentNode.specialVertex = jd_ntd.specialVertex.get(jd_currentBag);
                    checkSum++;
                    break;
                case JOIN:
                    currentNode.nodeType = NtdNode.NodeType.JOIN;
                    currentNode.firstChild = nodes.get(jd_currentNeighbourhood.get(0).id - 1);
                    currentNode.secondChild = nodes.get(jd_currentNeighbourhood.get(1).id - 1);
                    jd_bag_stack.push(jd_currentNeighbourhood.get(0));
                    jd_bag_stack.push(jd_currentNeighbourhood.get(1));
                    ntd.numberOfJoinNodes++;
                    checkSum++;
                    break;
            }
        }
        if (checkSum != numberOfBags) {
            throw new RuntimeException("The NTD was not transferred correctly\n");
        }

        
        ntd.computePathIndices();

        
        if (Settings.saveNtdGraphviz) {
            logger.info("Graphviz generation is started...");
            String graphviz = ntd.toGraphviz();
            try {
                File outputFile = getFreeFileName(Main.sessionOutputFolder + "/graphviz/ntd.dot");
                Files.write(outputFile.toPath(), graphviz.getBytes(), StandardOpenOption.CREATE);
            } catch (IOException e) {
                logger.info("IOException\n",e);
            }
            logger.info("Graphviz generation done.");
        }

        return ntd;
    }

    public static Ntd copyNtd(Ntd originalNtd, NtdNode newRoot) {
        
        if (!((newRoot.nodeType == FORGET && newRoot.bag.isEmpty()) || (newRoot.nodeType == LEAF))) {
            logger.error("copyNtd: invalid root");
            return null;
        }

        
        Ntd newNtd = new Ntd();

        

        newNtd.tw = originalNtd.tw;
        newNtd.numberOfNodes = originalNtd.numberOfNodes;
        newNtd.numberOfJoinNodes = originalNtd.numberOfJoinNodes;

        
        boolean foundNewRoot = originalNtd.root == newRoot;

        HashMap<NtdNode, NtdNode> parentNodeMap = new HashMap<>();
        Stack<NtdNode> nodeStack = new Stack<>();
        nodeStack.push(originalNtd.getRoot());
        parentNodeMap.put(originalNtd.getRoot().getFirstChild(), originalNtd.getRoot());

        while (!nodeStack.isEmpty()) {
            NtdNode currentNode = nodeStack.pop();

            if (currentNode.getNodeType() == LEAF) {
                if(currentNode == newRoot) foundNewRoot = true;
            } else if (currentNode.getNodeType() == JOIN) {
                nodeStack.push(currentNode.getFirstChild());
                nodeStack.push(currentNode.getSecondChild());
                parentNodeMap.put(currentNode.getFirstChild(), currentNode);
                parentNodeMap.put(currentNode.getSecondChild(), currentNode);
            } else {
                nodeStack.push(currentNode.getFirstChild());
                parentNodeMap.put(currentNode.getFirstChild(), currentNode);
            }
        }
        if(!foundNewRoot) throw new IllegalArgumentException("The new root must occur in the original ntd");


        AtomicInteger checkSum = new AtomicInteger(0);

        if (newRoot.nodeType == LEAF) {
            newNtd.root = recursiveUpwardsCopy(newRoot, parentNodeMap, checkSum);

        } else { 
            newNtd.root = recursiveDownwardsCopy(newRoot, checkSum);
        }

        if (checkSum.get() != newNtd.numberOfNodes) {
            logger.error("The NTD was not transferred correctly");
            throw new RuntimeException("The NTD was not transferred correctly");
        }

        

        
        

        
        newNtd.computePathIndices();

        

















        return newNtd;
    }
    private static NtdNode recursiveDownwardsCopy(NtdNode originalCurrentNode, AtomicInteger checksum) {
        NtdNode newCurrentNode = new NtdNode(originalCurrentNode.id);
        checksum.addAndGet(1);

        
        newCurrentNode.bag = new HashSet<>(originalCurrentNode.bag);
        
        newCurrentNode.nodeType = originalCurrentNode.nodeType;

        
        switch (originalCurrentNode.nodeType) {
            case LEAF:
                break;
            case JOIN:
                newCurrentNode.firstChild = recursiveDownwardsCopy(originalCurrentNode.firstChild, checksum);
                newCurrentNode.secondChild = recursiveDownwardsCopy(originalCurrentNode.secondChild, checksum);
                break;
            case FORGET,INTRODUCE:
                newCurrentNode.firstChild = recursiveDownwardsCopy(originalCurrentNode.firstChild, checksum);
                newCurrentNode.specialVertex = originalCurrentNode.getSpecialVertex();
                break;
        }
        return newCurrentNode;
    }

    private static NtdNode recursiveUpwardsCopy(NtdNode originalCurrentNode, HashMap<NtdNode, NtdNode> parentNodeMap, AtomicInteger checksum) {
        NtdNode newCurrentNode = new NtdNode(originalCurrentNode.id);
        checksum.addAndGet(1);

        
        newCurrentNode.bag = new HashSet<>(originalCurrentNode.bag);

        
        NtdNode parentNode = parentNodeMap.get(originalCurrentNode);
        if (parentNode == null) { 
            newCurrentNode.nodeType = LEAF;
            return newCurrentNode;
        }
        switch (parentNode.nodeType) {
            case JOIN:
                newCurrentNode.nodeType = JOIN;
                newCurrentNode.firstChild = parentNode.firstChild == originalCurrentNode ?
                        recursiveDownwardsCopy(parentNode.secondChild, checksum) :
                        recursiveDownwardsCopy(parentNode.firstChild, checksum);
                newCurrentNode.secondChild = recursiveUpwardsCopy(parentNode, parentNodeMap, checksum);
                break;
            case FORGET:
                newCurrentNode.nodeType = INTRODUCE;
                newCurrentNode.firstChild = recursiveUpwardsCopy(parentNode, parentNodeMap, checksum);
                newCurrentNode.specialVertex = parentNode.specialVertex;
                break;
            case INTRODUCE:
                newCurrentNode.nodeType = FORGET;
                newCurrentNode.specialVertex = parentNode.specialVertex;

                newCurrentNode.firstChild = recursiveUpwardsCopy(parentNode, parentNodeMap, checksum);
                break;
        }
        return newCurrentNode;
    }


    public static void fuseIntroduceJoinForgetNodes(Ntd ntd) {
       fuseIntroduceJoinForgetNodesRecursive(ntd.getRoot());
    }

    private static void fuseIntroduceJoinForgetNodesRecursive(NtdNode currentNode) {
        if (currentNode == null) return;

        if (currentNode.nodeType == JOIN_FORGET) {
            
            List<Integer> firstChildIntroducedVertices = new ArrayList<>();
            List<Integer> secondChildIntroducedVertices = new ArrayList<>();

            NtdNode firstChildTmpNode = currentNode.getFirstChild();

            
            while (firstChildTmpNode.nodeType == INTRODUCE) {
                firstChildIntroducedVertices.add(firstChildTmpNode.specialVertex);
                firstChildTmpNode = firstChildTmpNode.getFirstChild();
            }
            currentNode.firstChild = firstChildTmpNode;
            firstChildTmpNode = null;

            NtdNode secondChildTmpNode = currentNode.getSecondChild();

            
            while (secondChildTmpNode.nodeType == INTRODUCE) {
                secondChildIntroducedVertices.add(secondChildTmpNode.specialVertex);
                secondChildTmpNode = secondChildTmpNode.getFirstChild();
            }
            currentNode.secondChild = secondChildTmpNode;

            if(!firstChildIntroducedVertices.isEmpty() || !secondChildIntroducedVertices.isEmpty()){
                firstChildIntroducedVertices.sort(Comparator.naturalOrder());
                secondChildIntroducedVertices.sort(Comparator.naturalOrder());
                currentNode.firstChildIntroducedVertices = firstChildIntroducedVertices;
                currentNode.secondChildIntroducedVertices = secondChildIntroducedVertices;
                currentNode.nodeType = INTRODUCE_JOIN_FORGET;

            }
        }

        
        if (currentNode.getFirstChild() != null)
            fuseIntroduceJoinForgetNodesRecursive(currentNode.getFirstChild());

        if (currentNode.getSecondChild() != null)
            fuseIntroduceJoinForgetNodesRecursive(currentNode.getSecondChild());
    }

}
