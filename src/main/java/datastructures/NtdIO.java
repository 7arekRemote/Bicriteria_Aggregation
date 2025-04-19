package datastructures;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static utils.IO.getFreeFileName;

import java.io.*;
import java.util.*;

import static datastructures.NtdNode.NodeType.*;

public class NtdIO {
    static private final Logger logger = LoggerFactory.getLogger(NtdIO.class);


    public static File writeNtd(Ntd ntd, String outputFileName) {
        try {
            File outputFile = getFreeFileName(outputFileName);
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

            

            writer.write(String.format("c #Nodes #JoinNodes tw\nntd %d %d %d\n",
                    ntd.numberOfNodes, ntd.numberOfJoinNodes, ntd.tw));


            
            int currentID = 1;
            HashMap<NtdNode, Integer> id = new HashMap<>();

            Set<NtdNode> visited = new HashSet<>();
            Stack<NtdNode> stack = new Stack<>();
            stack.push(ntd.getRoot());

            
            while (!stack.isEmpty()) {
                NtdNode node = stack.peek();
                if (visited.contains(node)) {
                    stack.pop();
                    
                    id.put(node, currentID);

                    
                    
                    StringBuilder sb = new StringBuilder();
                    sb.append("b ").append(currentID);

                    for (Integer vertex : node.bag) {
                        sb.append(" ").append(vertex);
                    }
                    
                    switch (node.getNodeType()) {
                        case LEAF -> sb.append(" l");
                        case INTRODUCE -> sb.append(" i ").append(node.specialVertex).append(" ").append(id.get(node.firstChild));
                        case FORGET -> sb.append(" f ").append(node.specialVertex).append(" ").append(id.get(node.firstChild));
                        case JOIN -> sb.append(" j ").append(id.get(node.firstChild)).append(" ").append(id.get(node.secondChild));
                    }
                    sb.append("\n");
                    
                    writer.write(sb.toString());
                    currentID++;

                    continue;
                }
                visited.add(node);
                if (node.getFirstChild() != null) {
                    stack.push(node.getFirstChild());
                    if(node.getSecondChild() != null)
                        stack.push(node.getSecondChild());
                }
            }

            writer.close();
            return outputFile;

        } catch (IOException e) {
            logger.error("IOException\n",e);
            return null;
        }
    }

    public static Ntd readNtd(String inputFileName, boolean computePathIndices) {
        File inputFile = new File(inputFileName);

        Ntd ntd = new Ntd();
        ArrayList<NtdNode> nodes = new ArrayList<>();

        int checkSum = 0; 

        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {

            String line;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("c")) {
                    
                    continue;
                } else if (line.startsWith("ntd")) {

                    
                    String[] args = line.split(" ");
                    ntd.numberOfNodes = Integer.parseInt(args[1]);
                    ntd.numberOfJoinNodes = Integer.parseInt(args[2]);
                    ntd.tw = Integer.parseInt(args[3]);

                    for (int i = 0; i < ntd.numberOfNodes; i++) {
                        nodes.add(new NtdNode(i+1));
                    }

                } else {

                    
                    String[] args = line.split(" ");
                    Set<Integer> bag = new HashSet<>();
                    int id = Integer.parseInt(args[1]);
                    int i = 2; 

                    
                    while (true) {
                        try {
                            bag.add(Integer.parseInt(args[i]));
                            i++;
                        } catch (NumberFormatException e) {
                            break;
                        }
                    }

                    
                    switch (args[i]) {
                        case "l" -> {
                            nodes.get(id - 1).bag = bag;
                            nodes.get(id - 1).nodeType = LEAF;
                            checkSum++;
                        }
                        case "i" -> {
                            nodes.get(id - 1).bag = bag;
                            nodes.get(id - 1).specialVertex = Integer.parseInt(args[i + 1]);
                            nodes.get(id - 1).firstChild = nodes.get(Integer.parseInt(args[i + 2]) - 1);
                            nodes.get(id - 1).nodeType = INTRODUCE;
                            checkSum++;
                        }
                        case "f" -> {
                            nodes.get(id - 1).bag = bag;
                            nodes.get(id - 1).specialVertex = Integer.parseInt(args[i + 1]);
                            nodes.get(id - 1).firstChild = nodes.get(Integer.parseInt(args[i + 2]) - 1);
                            nodes.get(id - 1).nodeType = FORGET;
                            if(bag.isEmpty())
                                ntd.root = nodes.get(id - 1);
                            checkSum++;
                        }
                        case "j" -> {
                            nodes.get(id - 1).bag = bag;
                            nodes.get(id - 1).firstChild = nodes.get(Integer.parseInt(args[i + 1]) - 1);
                            nodes.get(id - 1).secondChild = nodes.get(Integer.parseInt(args[i + 2]) - 1);
                            nodes.get(id - 1).nodeType = JOIN;
                            checkSum++;
                        }
                    }

                }
            }

        } catch (Exception e) {
            logger.error("Exception:\n",e);
            throw new RuntimeException();
        }
        if (checkSum != ntd.numberOfNodes) {
            logger.error("The NTD was not output correctly\n");
            throw new RuntimeException();
        }

        if (computePathIndices) {
            
            ntd.computePathIndices();
        }

        return ntd;
    }

}
