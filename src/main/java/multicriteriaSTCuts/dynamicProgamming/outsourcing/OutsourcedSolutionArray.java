package multicriteriaSTCuts.dynamicProgamming.outsourcing;

import datastructures.Ntd;
import datastructures.NtdNode;
import multicriteriaSTCuts.Solution;
import multicriteriaSTCuts.dynamicProgamming.MincutDynprog;
import multicriteriaSTCuts.dynamicProgamming.MincutSolutionVector;
import multicriteriaSTCuts.dynamicProgamming.SolutionPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.IO;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;

import static multicriteriaSTCuts.dynamicProgamming.outsourcing.OutsourceHandler.*;
import static utils.IO.deleteDir;



public class OutsourcedSolutionArray extends SolutionArray{

    private static final Logger logger = LoggerFactory.getLogger(OutsourcedSolutionArray.class);
    private final MincutDynprog dynprog;
    private File arrayFolder;


    public OutsourcedSolutionArray(int stackIdx, MincutDynprog dynprog) {
        this.dynprog = dynprog;
        
        OutsourceHandler.initArrayFolder(stackIdx, this);
    }


    @Override
    public synchronized void set(int idx, ArrayList<SolutionPointer> entry) {

        File entryFile = new File(arrayFolder + "/" + idx + ".data");

        
        if(entry == null){
            deleteDir(entryFile,false);
            return;
        }

        
        IO.tryCreateFile(entryFile,false);
        try (BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(entryFile))) {
            for (int i = 0; i < entry.size(); i++) {
                SolutionPointer solutionPointer = entry.get(i);

                
                if (solutionPointer.getId() == -1) {
                    createOriginPointerEntry(solutionPointer);
                }
                writer.write(toSurfacePointerBytes(solutionPointer));
            }
        } catch (IOException e) {
            logger.error("Writing in File " + entryFile.getAbsolutePath() + " has thrown an IOExeption",e);
            throw new RuntimeException("Writing in File " + entryFile.getAbsolutePath() + " has thrown an IOExeption");
        }
    }

    
    @Override
    public synchronized ArrayList<SolutionPointer> get(int idx) {
        
        File entryFile = new File(arrayFolder + "/" + idx + ".data");

        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(entryFile))){
            ArrayList<SolutionPointer> solutionPointers = new ArrayList<>();

            
            while (true) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(OutsourceHandler.SURFACE_POINTER_BYTES); 
                if (inputStream.read(byteBuffer.array()) != OutsourceHandler.SURFACE_POINTER_BYTES)
                    break;
                solutionPointers.add(getSurfacePointerFromBytes(byteBuffer));
            }
            return solutionPointers;

        } catch (FileNotFoundException e) {
            logger.error("get() may only be called for existing (i.e. != null) entries!",e);
            throw new RuntimeException("get() may only be called for existing (i.e. != null) entries!");
        } catch (IOException e) {
            logger.error("Reading in File " + entryFile.getAbsolutePath() + " has thrown an IOExeption",e);
            throw new RuntimeException("Reading in File " + entryFile.getAbsolutePath() + " has thrown an IOExeption");
        }
    }

    @Override
    public int getEntrySize(int idx) {
        
        File entryFile = new File(arrayFolder + "/" + idx + ".data");

        try {
            return (int) (Files.size(entryFile.toPath()) / OutsourceHandler.SURFACE_POINTER_BYTES);
        } catch (IOException e) {
            logger.error("Determining the size of File " + entryFile.getAbsolutePath() + " has thrown an IOExeption",e);
            throw new RuntimeException("Determining the size of File " + entryFile.getAbsolutePath() + " has thrown an IOExeption");
        }
    }

    @Override
    public int getEntryCount() {
        return arrayFolder.list().length;
    }

    @Override
    public List<Solution> getSolutions(Ntd ntd) {
        
        ntd.createNodeIdMap();

        ArrayList<SolutionPointer> solutionPointers = get(0);

        ArrayList<Solution> solutions = new ArrayList<>(solutionPointers.size());

        
        dynprog.getOutsourceHandler().flushOriginStream();

        
        try (RandomAccessFile raf = new RandomAccessFile(dynprog.getOutsourceHandler().getOriginPointerFile(), "r")){
            for (SolutionPointer solutionPointer : solutionPointers) {
                
                ArrayList<Integer> vertices = new ArrayList<>();

                Stack<Long> idStack = new Stack<>();
                idStack.push(solutionPointer.getId());
                while (!idStack.isEmpty()) {
                    long currentID = idStack.pop();

                    
                    ByteBuffer byteBuffer = ByteBuffer.allocate(OutsourceHandler.ORIGIN_POINTER_BYTES);
                    raf.seek(currentID * OutsourceHandler.ORIGIN_POINTER_BYTES);
                    raf.readFully(byteBuffer.array());
                    long firstLong = byteBuffer.getLong();

                    
                    long secondLong = byteBuffer.getLong()& ~LONG_3_MSB_MASK;

                    if((firstLong & LONG_2_MSB_MASK) != 0){
                        

                        
                        long originId = firstLong;
                        originId = originId & ~LONG_2_MSB_MASK; 
                        boolean isFromFirstChild = (originId & LONG_3_MSB_MASK) != 0; 
                        if(isFromFirstChild)
                            originId = originId & ~LONG_3_MSB_MASK; 

                        
                        idStack.push(originId);

                        
                        int joinNodeId = (int) (secondLong >> 32);
                        int ivMask = (int) secondLong;

                        
                        NtdNode node = ntd.nodeMap.get(joinNodeId);

                        
                        List<Integer> sideIntroducedVertices = isFromFirstChild ?
                                node.getFirstChildIntroducedVertices() : node.getSecondChildIntroducedVertices();

                        List<Integer> S_side_introduced_vertices = MincutSolutionVector.reverseListMask(ivMask, sideIntroducedVertices);

                        for (int i = 0; i < S_side_introduced_vertices.size(); i++) {
                            if(!vertices.contains(S_side_introduced_vertices.get(i)))
                                vertices.add(S_side_introduced_vertices.get(i));
                        }

                    } else if ((firstLong & LONG_4_MSB_MASK) != 0) {
                        
                        int vertex = (int) (firstLong & ~LONG_4_MSB_MASK);
                        if (!vertices.contains(vertex))
                            vertices.add(vertex);
                        idStack.push(secondLong);
                    } else if (firstLong == Long.MIN_VALUE) {
                        
                    } else {
                        
                        idStack.push(firstLong);
                        idStack.push(secondLong);
                    }
                }

                Collections.sort(vertices);
                solutions.add(new Solution(solutionPointer.getWeight(), vertices));
            }
        } catch (FileNotFoundException e) {
            logger.error("The Origin-Pointer File was not found",e);
            throw new RuntimeException("The Origin-Pointer File was not found");
        } catch (IOException e) {
            logger.error("Reading the originPointer file has thrown an IOExeption",e);
            throw new RuntimeException("Reading the originPointer file has thrown an IOExeption");
        }

        return solutions;
    }

    @Override
    public Iterator<Integer> getDebugNonNullIndexIterator(boolean ascending, int vBinary) {
        ArrayList<Integer> indices = getNonNullIndices(vBinary);

        
        if (ascending) {
            indices.sort(Integer::compareTo);
        } else {
            indices.sort(Collections.reverseOrder());
        }

        return indices.iterator();
    }

    public ArrayList<Integer> getNonNullIndices(int vBinary) {
        
        String[] files = arrayFolder.list();

        
        for (int i = 0; i < files.length; i++) {
            files[i] = files[i].substring(0, files[i].lastIndexOf('.'));
        }

        
        ArrayList<Integer> indices = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {
            int idx = Integer.parseInt(files[i]);
            if((idx & vBinary) != 0)
                continue;
            indices.add(idx);
        }
        return indices;
    }

    public void deleteEntryFile(int idx) {
        
        File entryFile = new File(arrayFolder + "/" + idx + ".data");
        deleteDir(entryFile,false);
    }


    public void deleteFolder() {
        deleteDir(arrayFolder,false);
    }

    public void addFolderSuffix(String suffix) {
        arrayFolder = IO.addFolderSuffix(arrayFolder, suffix,true);
    }

    public File getArrayFolder() {
        return arrayFolder;
    }

    public void setArrayFolder(File arrayFolder) {
        this.arrayFolder = arrayFolder;
    }

    private byte[] toSurfacePointerBytes(SolutionPointer solutionPointer) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(OutsourceHandler.SURFACE_POINTER_BYTES);
        
        byteBuffer.putLong(solutionPointer.getId());
        
        double[] weight = solutionPointer.getWeight();
        for (int i = 0; i < weight.length; i++) {
            byteBuffer.putDouble(weight[i]);
        }
        return byteBuffer.array();
    }

    private static byte[] toOriginPointerBytes(SolutionPointer sp) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(OutsourceHandler.ORIGIN_POINTER_BYTES);
        if (sp.joinNodeId != -1) {
            
            
            

            long firstLong = sp.id; 
            
            firstLong |= LONG_2_MSB_MASK;
            
            if (sp.isFromFirstChild)
                firstLong |= LONG_3_MSB_MASK;

            long secondLong = 0;
            
            secondLong |= sp.ivMask;
            
            secondLong |= ((long) sp.joinNodeId) << 32;


            byteBuffer.putLong(firstLong);
            byteBuffer.putLong(secondLong);
            sp.joinNodeId = -1; 

        } else if(sp.getVertex() != null) {
            
            byteBuffer.putLong(sp.getVertex() | LONG_4_MSB_MASK);
            byteBuffer.putLong(sp.getSolutionOrigin().getId());
        } else if (sp.getSolutionOrigin() != null){
            
            byteBuffer.putLong(sp.getSolutionOrigin().getId());
            byteBuffer.putLong(sp.getSecondSolutionOrigin().getId());
        } else {
            
            byteBuffer.putLong(Long.MIN_VALUE);
            byteBuffer.putLong(Long.MIN_VALUE);
        }
        return byteBuffer.array();
    }

    public void createOriginPointerEntry(SolutionPointer solutionPointer) {

        
        byte[] bytes = toOriginPointerBytes(solutionPointer);

        synchronized (dynprog.getOutsourceHandler().getOriginPointerFile()) {
            
            solutionPointer.setId(dynprog.getOutsourceHandler().getAndAddFirstFreePointerID());

            
            dynprog.getOutsourceHandler().writeToOriginPointer(bytes);
        }

        
        if (solutionPointer.getId() < 0) {
            logger.error("OriginPointer ID overflow -> long must be replaced by BigInteger");
            throw new RuntimeException("OriginPointer ID overflow -> long must be replaced by BigInteger");
        }

        
        solutionPointer.setSolutionOrigin(null);
        solutionPointer.setSecondSolutionOrigin(null);

    }


    private SolutionPointer getSurfacePointerFromBytes(ByteBuffer byteBuffer) {
        
        long id = byteBuffer.getLong();
        double[] weight = new double[dynprog.mincutGraph.getWeightDimension()];
        for (int i = 0; i < dynprog.mincutGraph.getWeightDimension(); i++) {
            weight[i] = byteBuffer.getDouble();
        }
        
        return new SolutionPointer(weight, id);
    }
}
