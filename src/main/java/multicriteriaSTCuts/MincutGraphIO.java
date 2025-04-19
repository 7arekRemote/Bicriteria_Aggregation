package multicriteriaSTCuts;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

import static utils.IO.getFreeFileName;


public class MincutGraphIO {

    static private final Logger logger = LoggerFactory.getLogger(MincutGraphIO.class);

    public static MincutGraph readGraphFromDataset(File verticesFile, File adjacenciesFile) {
        MincutGraph mincutGraph = new MincutGraph(2);

        int zID = Integer.MIN_VALUE;


        
        try (BufferedReader br = new BufferedReader(new FileReader(verticesFile))) {
            String line;

            
            br.readLine();

            boolean isFirstClassOne = true;

            while ((line = br.readLine()) != null) {
                String[] fields = line.split(",");
                int id = Integer.parseInt(fields[0]);
                int class_ = Integer.parseInt(fields[1]);
                double area = Double.parseDouble(fields[2]);

                if (class_ == -1) {
                    
                    zID = id;
                } else if (class_ == 0) {
                    
                    mincutGraph.increaseEdgeWeight("s","t",new double[]{area,0});
                } else if (class_ == 1) {
                    
                    
                    if (isFirstClassOne) {
                        isFirstClassOne = false;
                        mincutGraph.idOffset = id;
                    }
                    
                    mincutGraph.increaseEdgeWeight("t",id- mincutGraph.idOffset,new double[]{area,0});
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        
        try (BufferedReader br = new BufferedReader(new FileReader(adjacenciesFile))) {
            String line;

            
            br.readLine();

            while ((line = br.readLine()) != null) {
                String[] fields = line.split(",");
                int id1 = Integer.parseInt(fields[0]);
                int id2 = Integer.parseInt(fields[1]);
                double length = Double.parseDouble(fields[2]);

                boolean swappedIDs = false;
                
                if (id1 > id2) {
                    swappedIDs = true;
                    int tmp = id1;
                    id1 = id2;
                    id2 = tmp;
                }

                
                if (id1 == zID) {
                    
                    if (id2 < mincutGraph.idOffset) {
                        
                        mincutGraph.increaseEdgeWeight("s","t",new double[]{0,length});

                    } else {
                        
                        mincutGraph.increaseEdgeWeight("t",id2- mincutGraph.idOffset,new double[]{0,length});
                    }
                } else if (id1 < mincutGraph.idOffset && id2 >= mincutGraph.idOffset) {
                    
                    mincutGraph.increaseEdgeWeight("s",id2- mincutGraph.idOffset,new double[]{0,length});
                } else if (id1 >= mincutGraph.idOffset) {
                    
                    mincutGraph.increaseEdgeWeight(id1 - mincutGraph.idOffset, id2 - mincutGraph.idOffset, new double[]{0, length});
                } else {
                    
                    throw new Exception(String.format("Such an edge should not occur: " +
                            "id1=%d,id2=%d,MincutGraph.idOffset=%d",
                            swappedIDs ? id2 : id1,
                            swappedIDs ? id1 : id2,
                            mincutGraph.idOffset));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return mincutGraph;
    }

    public static void writeGraphToTxt(MincutGraph mincutGraph, String file) {
        try {
            File outputFile = getFreeFileName(file);
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

            
            writer.write("c #vertices #edges weightDimension idOffset\n");
            writer.write(String.format("mincutGraph %d %d %d %d\n",
                    mincutGraph.getJd_graph().getNumVertices(),
                    mincutGraph.getJd_graph().getNumberOfEdges(),
                    mincutGraph.getWeightDimension(),
                    mincutGraph.idOffset));


            writer.write("c u v weight[0] weight[1] ...\n");

            
            if(mincutGraph.getEdgeWeight("s","t") != null)
                writer.write("s t " + getWeightString(mincutGraph.getEdgeWeight("s","t"))+ "\n");

            
            for (Integer u : mincutGraph.getJd_graph().getCopyOfVertices()) {
                double[] weight;

                if((weight = mincutGraph.getEdgeWeight("s",u)) != null)
                    writer.write("s " + u + " " + getWeightString(weight) + "\n");

                if((weight = mincutGraph.getEdgeWeight("t",u)) != null)
                    writer.write("t " + u + " " + getWeightString(weight) + "\n");

                for (Integer v : mincutGraph.getJd_graph().getNeighborhood(u)) {
                    if (u.compareTo(v) > 0) continue;
                    writer.write(u + " " + v + " " + getWeightString(mincutGraph.getEdgeWeight(u, v)) + "\n");
                }
            }
            writer.close();

        } catch (IOException e) {
            logger.error("IOException",e);
        }
    }

    public static MincutGraph readGraphFromTxt(String inputFile) {
        MincutGraph mincutGraph = null;

        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {

            String line;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("c")) {
                    
                    continue;
                } else if (line.startsWith("mincutGraph")) {

                    
                    String[] args = line.split(" ");

                    
                    mincutGraph = new MincutGraph(Integer.parseInt(args[3]));
                    mincutGraph.idOffset = Integer.parseInt(args[4]);

                } else {
                    assert mincutGraph != null;

                    
                    String[] args = line.split(" ");


                    double[] weight = new double[mincutGraph.getWeightDimension()];

                    for (int i = 0; i < mincutGraph.getWeightDimension(); i++) {
                        weight[i] = Double.parseDouble(args[i + 2]);
                    }


                    if (args[0].equals("s") || args[0].equals("t")) {
                        if (args[1].equals("s") || args[1].equals("t")) {
                            mincutGraph.increaseEdgeWeight("s", "t", weight);
                        } else {
                            int v = Integer.parseInt(args[1]);
                            mincutGraph.increaseEdgeWeight(args[0], v, weight);
                        }
                    } else {
                        int u = Integer.parseInt(args[0]);
                        int v = Integer.parseInt(args[1]);

                        mincutGraph.increaseEdgeWeight(u,v, weight);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("ERROR: mincutGraph could not be created",e);
            throw new RuntimeException();
        }
        return mincutGraph;
    }

    private static String getWeightString(double[] weight) {
        String string = String.valueOf(weight[0]);
        for (int i = 1; i < weight.length; i++) {
            string = string + " " + weight[i];
        }
        return string;
    }


}
