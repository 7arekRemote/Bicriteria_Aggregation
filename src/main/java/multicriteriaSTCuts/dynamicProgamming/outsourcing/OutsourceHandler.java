package multicriteriaSTCuts.dynamicProgamming.outsourcing;

import dataLogging.DataLog;
import datastructures.NtdNode;
import main.Settings;
import multicriteriaSTCuts.benchmark.Benchmark;
import multicriteriaSTCuts.dynamicProgamming.MincutDynprog;
import multicriteriaSTCuts.dynamicProgamming.MincutSolutionVector;
import multicriteriaSTCuts.dynamicProgamming.SolutionPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.IO;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class OutsourceHandler implements Closeable {


    private static Logger logger = LoggerFactory.getLogger(OutsourceHandler.class);
    public static final int ORIGIN_POINTER_BYTES = 2 * Long.BYTES;
    static int SURFACE_POINTER_BYTES;




    private BufferedOutputStream originPointerStream;

    private final File originPointerFile;

    private long firstFreePointerID = 0;
    public static final long LONG_2_MSB_MASK = 0x4000000000000000L;
    public static final long LONG_3_MSB_MASK = 0x2000000000000000L;
    public static final long LONG_4_MSB_MASK = 0x1000000000000000L;

    private static boolean outsourceWasEmpty = false;
    private static boolean areOnSamePartition = true;

    public static long firstFreeDEBUG = 0;

    private long preForgetNumStackBytes = -1;
    private long stackBytesDiffSum = 0;
    private boolean isInForgetSeries = false;

    private MincutDynprog dynprog;


    public OutsourceHandler(MincutDynprog dynprog) throws IOException {
        this.dynprog = dynprog;
        SURFACE_POINTER_BYTES = Long.BYTES + dynprog.mincutGraph.getWeightDimension() * Double.BYTES;



        OutsourceHandler.deleteOutsourcedData();


        originPointerFile = initOriginPointerFile();
        originPointerStream = new BufferedOutputStream(new FileOutputStream(originPointerFile));


        firstFreeDEBUG = 0;
    }

    @Override
    public void close() throws IOException {

        originPointerStream.close();
    }

    public static void initStackFolder() {

        if (Settings.getStackOutsourceFolder() == null || Settings.getOriginPointerOutsourceFolder() == null) {
            logger.error("No outsourceFolder has been set via the settings.");
            System.exit(3387);
        }


        if (Settings.getStackOutsourceFolder().toPath().startsWith(Settings.getOriginPointerOutsourceFolder().toPath()) ||
                Settings.getOriginPointerOutsourceFolder().toPath().startsWith(Settings.getStackOutsourceFolder().toPath())) {
            logger.error("The surfacePointer folder must not be in the originPointer folder, and vice versa.");
            System.exit(3388);
        }



        logger.info("SurfacePointer folder: {}",Settings.getStackOutsourceFolder());
        logger.info("OriginPointer folder: {}",Settings.getOriginPointerOutsourceFolder());


        try {
            if (!Settings.getStackOutsourceFolder().mkdirs()) {
                DirectoryStream<Path> ds = Files.newDirectoryStream(Settings.getStackOutsourceFolder().toPath());
                boolean isNotEmpty = ds.iterator().hasNext();
                ds.close();
                if (isNotEmpty) {

                    logger.error("The surfacePointer folder is *not* empty. Stopping to avoid accidentally deleting important data.");
                    logger.error("Please specify a different folder or delete all files within the folder.");
                    System.exit(3345);
                }
            }
            if (!Settings.getOriginPointerOutsourceFolder().mkdirs()) {
                DirectoryStream<Path> ds = Files.newDirectoryStream(Settings.getOriginPointerOutsourceFolder().toPath());
                boolean isNotEmpty = ds.iterator().hasNext();
                ds.close();
                if (isNotEmpty) {

                    logger.error("The originPointer folder is *not* empty. Stopping to avoid accidentally deleting important data.");
                    logger.error("Please specify a different folder or delete all files within the folder.");
                    System.exit(3346);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            areOnSamePartition = Files.getFileStore(Settings.getStackOutsourceFolder().toPath())
                    .equals(Files.getFileStore(Settings.getOriginPointerOutsourceFolder().toPath()));
            if (areOnSamePartition) {
                logger.info("The outsource folders are located on the *same* partition.");
            } else {
                logger.info("The outsource folders are located on *different* partitions.");
            }
        } catch (IOException e) {
            logger.warn("Could not determine partitions of the outsource folders. It is assumed that they are on the *same* partition.",e);
        }

        outsourceWasEmpty = true;
    }

    public static File initOriginPointerFile() {
        File originPointerFile = new File(Settings.getOriginPointerOutsourceFolder() + "/originPointer.data");
        IO.tryCreateFile(originPointerFile, true);
        return originPointerFile;
    }

    public static void initArrayFolder(int stackIdx,OutsourcedSolutionArray solutionArray) {
        solutionArray.setArrayFolder(new File(Settings.getStackOutsourceFolder() + "/" + stackIdx));
        boolean operationSuccess = solutionArray.getArrayFolder().mkdirs();
        if(!operationSuccess) throw new RuntimeException("An array folder could not be created.");
    }

    public static void deleteOutsourcedData() {
        if (Settings.getStackOutsourceFolder() == null|| Settings.getOriginPointerOutsourceFolder() == null) {
            return;
        }

        logger.info("Deleting outsourced data (if present)...");

        long startTime = System.nanoTime();

        IO.deleteDir(Settings.getOriginPointerOutsourceFolder(), true);
        IO.deleteDir(Settings.getStackOutsourceFolder(), true);

        long endTime = System.nanoTime();


        if(Benchmark.currentResult != null)
            Benchmark.currentResult.pareto_mincut_time_offset += (long) ((endTime - startTime) / 1_000_000_000.0);

        logger.info((String.format(
                "Outsourced data deleted within %.3f seconds.",
                (endTime - startTime) / 1_000_000_000.0)));
    }

    public void checkForPruning(NtdNode node) {


        if ((node.getNodeType().toString().contains("FORGET")) && !isInForgetSeries) {
            preForgetNumStackBytes = getFolderSizeWithRetry(Settings.getStackOutsourceFolder().toString());
            isInForgetSeries = true;
        }


        if (node.getNodeType() != NtdNode.NodeType.FORGET && isInForgetSeries) {


            long postForgetNumStackBytes = getFolderSizeWithRetry(Settings.getStackOutsourceFolder().toString());
            long stackSizeDiff = preForgetNumStackBytes - postForgetNumStackBytes;
            stackBytesDiffSum += stackSizeDiff;
            isInForgetSeries = false;
        }


        if (node.getNodeType() == NtdNode.NodeType.INTRODUCE || node.getNodeType().toString().contains("JOIN")) {




            long estimatedOPByteGrowth =
                    node.getNodeType() == NtdNode.NodeType.INTRODUCE ?
                        dynprog.solutionVectorStack.peek().getNumSolutions() * ORIGIN_POINTER_BYTES :
                    node.getNodeType() == NtdNode.NodeType.JOIN ?
                        Math.max(dynprog.solutionVectorStack.get(dynprog.solutionVectorStack.size()-1).getNumSolutions(),
                                dynprog.solutionVectorStack.get(dynprog.solutionVectorStack.size()-2).getNumSolutions())
                        * ORIGIN_POINTER_BYTES * 2 :
                    node.getNodeType() == NtdNode.NodeType.JOIN_FORGET ?
                        (long) (Math.max(dynprog.solutionVectorStack.get(dynprog.solutionVectorStack.size()-1).getNumSolutions(),
                        dynprog.solutionVectorStack.get(dynprog.solutionVectorStack.size()-2).getNumSolutions())
                        * ORIGIN_POINTER_BYTES * 2 / Math.pow(2,node.getForgottenVertices().size())):
                    node.getNodeType() == NtdNode.NodeType.INTRODUCE_JOIN_FORGET ?
                        (long) (Math.max(
                                dynprog.solutionVectorStack.get(dynprog.solutionVectorStack.size()-1).getNumSolutions()
                                    * Math.pow(2,node.getFirstChildIntroducedVertices().size()),
                                dynprog.solutionVectorStack.get(dynprog.solutionVectorStack.size()-2).getNumSolutions()
                                    * Math.pow(2,node.getSecondChildIntroducedVertices().size()))
                        * ORIGIN_POINTER_BYTES * 4 / Math.pow(2,node.getForgottenVertices().size())):
                    0;

            long estimatedStackByteGrowth =
                    node.getNodeType() == NtdNode.NodeType.INTRODUCE ?
                            dynprog.solutionVectorStack.peek().getNumSolutions() * SURFACE_POINTER_BYTES :
                    node.getNodeType() == NtdNode.NodeType.JOIN ?
                            0 :
                    node.getNodeType() == NtdNode.NodeType.JOIN_FORGET ?
                            (long) (Math.max(
                                        dynprog.solutionVectorStack.get(dynprog.solutionVectorStack.size()-1).getNumSolutions(),
                                        dynprog.solutionVectorStack.get(dynprog.solutionVectorStack.size()-2).getNumSolutions())
                                    * SURFACE_POINTER_BYTES * 3 / Math.pow(2,node.getForgottenVertices().size()))
                            - dynprog.solutionVectorStack.get(dynprog.solutionVectorStack.size()-1).getNumSolutions() * SURFACE_POINTER_BYTES
                            - dynprog.solutionVectorStack.get(dynprog.solutionVectorStack.size()-2).getNumSolutions() * SURFACE_POINTER_BYTES
                            :
                    node.getNodeType() == NtdNode.NodeType.INTRODUCE_JOIN_FORGET ?

                        (long) (Math.max(
                            dynprog.solutionVectorStack.get(dynprog.solutionVectorStack.size()-1).getNumSolutions()
                                    * Math.pow(2,node.getFirstChildIntroducedVertices().size()),
                            dynprog.solutionVectorStack.get(dynprog.solutionVectorStack.size()-2).getNumSolutions()
                                    * Math.pow(2,node.getSecondChildIntroducedVertices().size()))
                            * SURFACE_POINTER_BYTES * 3 / Math.pow(2,node.getForgottenVertices().size())):
                    0;

            long estimatedByteGrowth = areOnSamePartition ?
                    estimatedOPByteGrowth + estimatedStackByteGrowth :
                    estimatedOPByteGrowth;


            long stackFolderSize = getFolderSizeWithRetry(Settings.getStackOutsourceFolder().toString());
            long originPointerFileSize = getFolderSizeWithRetry(Settings.getOriginPointerOutsourceFolder().toString());

            long outsourcedDataSize = stackFolderSize + originPointerFileSize;


            long freeSpace = Settings.getOriginPointerOutsourceFolder().getFreeSpace();


            DataLog.prune(String.format(Locale.US,"%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,",
                    Benchmark.currentResult.graph_id, Benchmark.currentResult.ntd_nr,
                    (long) ((System.nanoTime() - Benchmark.currentResult.startTime) / 1_000_000.0),
                    outsourcedDataSize / (1024*1024f), stackFolderSize / (1024*1024f), originPointerFileSize / (1024*1024f),
                    stackBytesDiffSum / (1024*1024f),
                    freeSpace / (1024*1024f),
                    estimatedByteGrowth / (1024*1024f),
                    estimatedStackByteGrowth / (1024*1024f),
                    estimatedOPByteGrowth / (1024*1024f)
                ),false);


            if (
                    freeSpace-estimatedByteGrowth-Settings.pruneFreeSpacePuffer< 0 ||
                    outsourcedDataSize+estimatedByteGrowth > Settings.outsourcedSpaceLimit

            ) {
                if (Settings.deactivatePruning) {
                    logger.warn("Prunig is deactivated. It is expected that the outsource folder could become too large during the next node.");

                    DataLog.prune(String.format(Locale.US, "%s,,,,,",
                            "no"), true);
                } else {
                    long prePruneOriginLines = firstFreePointerID;
                    long startTime = System.nanoTime();
                    pruneOriginPointerFile();
                    long endTime = System.nanoTime();
                    long pruneMs = (endTime - startTime) / 1_000_000;

                    DataLog.prune(String.format(Locale.US,"%s,%d,%d,%d,%.2f,%d",
                            "yes",
                            prePruneOriginLines,firstFreePointerID,
                            prePruneOriginLines-firstFreePointerID, (prePruneOriginLines-firstFreePointerID)/(float)prePruneOriginLines,
                            pruneMs),true);

                    logger.trace(String.format("Pruning done. Elapsed time [sec]: %.3f",pruneMs / (float) 1000));
                }

                stackBytesDiffSum = 0;
            } else {

                DataLog.prune(String.format(Locale.US,"%s,,,,,",
                        "no"),true);
            }
        }
    }

    private long getFolderSizeWithRetry(String folder){
        long[] result;
        do {
            result = IO.getFolderSize(Path.of(folder));
            if (result[1] == 0) {
                logger.warn("The folder size was returned as 0. Trying again...");
            }
        } while (result[1] == 0);
        return result[0];
    }

    public void pruneOriginPointerFile() {
        logger.trace("Starte pruning...");

        try {
            originPointerStream.close();
        } catch (IOException e) {
            logger.error("IOException when closing the old OriginPointer Writer",e);
            throw new RuntimeException("IOException when closing the old OriginPointer Writer");
        }


        long newEntryCount = 0;

        try (RandomAccessFile raf = new RandomAccessFile(originPointerFile, "rw")) {


            for (MincutSolutionVector solutionVector : dynprog.solutionVectorStack) {


                for (Iterator<Integer> it = solutionVector.solutionArray.getDebugNonNullIndexIterator(true, 0); it.hasNext(); ) {
                    if (Thread.interrupted()) throw new RuntimeException();

                    int sBinary = it.next();
                    ArrayList<SolutionPointer> solutions = solutionVector.solutionArray.get(sBinary);


                    for (SolutionPointer solution : solutions) {
                        newEntryCount += recursiveMarkEntriesAsNeeded(raf, solution.getId());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        firstFreePointerID = 0;

        try (RandomAccessFile raf = new RandomAccessFile(originPointerFile, "rw")) {




            for (MincutSolutionVector solutionVector : dynprog.solutionVectorStack) {


                for (Iterator<Integer> it = solutionVector.solutionArray.getDebugNonNullIndexIterator(true, 0); it.hasNext(); ) {
                    if (Thread.interrupted()) throw new RuntimeException();

                    int sBinary = it.next();
                    ArrayList<SolutionPointer> solutions = solutionVector.solutionArray.get(sBinary);


                    for (SolutionPointer solution : solutions) {


                        solution.setId(recursiveTransferOriginEntry(raf, solution.getId(), newEntryCount));
                    }

                    solutionVector.solutionArray.set(sBinary, solutions);
                }
            }


            raf.seek(firstFreePointerID * ORIGIN_POINTER_BYTES);
            while (firstFreePointerID<newEntryCount) {
                long currentFirstLong = raf.readLong();
                long currentSecondLong = raf.readLong();

                if((currentSecondLong & LONG_2_MSB_MASK) != 0){
                    raf.seek(firstFreePointerID * ORIGIN_POINTER_BYTES);
                    raf.writeLong(currentFirstLong);
                    raf.writeLong(currentSecondLong & ~LONG_2_MSB_MASK);
                }
                firstFreePointerID++;
            }



            raf.setLength(newEntryCount * ORIGIN_POINTER_BYTES);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }



        try {
            originPointerStream = new BufferedOutputStream(new FileOutputStream(originPointerFile,true));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private long recursiveMarkEntriesAsNeeded(RandomAccessFile raf, long id) throws IOException {
        long newEntryCount = 0;

        raf.seek(id * ORIGIN_POINTER_BYTES);
        long firstLong = raf.readLong();
        long secondLong = raf.readLong();

        if ((secondLong & LONG_2_MSB_MASK) != 0) {

            return newEntryCount;
        }


        secondLong = secondLong & ~LONG_3_MSB_MASK;


        if (firstLong == Long.MIN_VALUE && secondLong == Long.MIN_VALUE) {


        } else if ((firstLong & LONG_2_MSB_MASK) != 0) {

            long originId = firstLong;
            originId = originId & ~LONG_2_MSB_MASK;
            originId = originId & ~LONG_3_MSB_MASK;
            newEntryCount += recursiveMarkEntriesAsNeeded(raf, originId);

        } else if ((firstLong & LONG_4_MSB_MASK) != 0) {

            newEntryCount += recursiveMarkEntriesAsNeeded(raf, secondLong);

        } else {

            newEntryCount += recursiveMarkEntriesAsNeeded(raf, firstLong);
            newEntryCount += recursiveMarkEntriesAsNeeded(raf, secondLong);
        }


        raf.seek(id * ORIGIN_POINTER_BYTES);
        raf.writeLong(firstLong);
        raf.writeLong(secondLong | LONG_2_MSB_MASK);
        return newEntryCount+1;
    }

    private long recursiveTransferOriginEntry(RandomAccessFile raf, long oldId, long newEntryCount) throws IOException {

        raf.seek(oldId * ORIGIN_POINTER_BYTES);
        long firstLong = raf.readLong();
        long secondLong = raf.readLong();

        long newId;
        long newFirstLong;
        long newSecondLong;











        if((secondLong & LONG_3_MSB_MASK) != 0){

            if (oldId < newEntryCount) {

                return oldId;
            } else {

                return firstLong;
            }
        }





        secondLong = secondLong & ~LONG_2_MSB_MASK;


        if((firstLong & LONG_2_MSB_MASK) != 0){



            long originId = firstLong;
            originId = originId & ~LONG_2_MSB_MASK;
            boolean isFromFirstChild = (originId & LONG_3_MSB_MASK) != 0;
            if(isFromFirstChild)
                originId = originId & ~LONG_3_MSB_MASK;


            newFirstLong = recursiveTransferOriginEntry(raf, originId, newEntryCount);


            newFirstLong = newFirstLong | LONG_2_MSB_MASK;
            if(isFromFirstChild)
                newFirstLong = newFirstLong | LONG_3_MSB_MASK;


            newSecondLong = secondLong;
        } else if ((firstLong & LONG_4_MSB_MASK) != 0) {

            newFirstLong = firstLong;
            newSecondLong = recursiveTransferOriginEntry(raf, secondLong, newEntryCount);
        } else if(secondLong == Long.MIN_VALUE){

            newFirstLong = Long.MIN_VALUE;
            newSecondLong = Long.MIN_VALUE;
        } else {

            newFirstLong = recursiveTransferOriginEntry(raf, firstLong, newEntryCount);
            newSecondLong = recursiveTransferOriginEntry(raf, secondLong, newEntryCount);
        }

        if (oldId < newEntryCount) {

            newId = oldId;


            newSecondLong = newSecondLong | LONG_3_MSB_MASK;


            if(firstFreePointerID <= oldId){
                newSecondLong = newSecondLong | LONG_2_MSB_MASK;
            }

        } else {



            raf.seek(firstFreePointerID * ORIGIN_POINTER_BYTES);
            while (true) {
                long currentFistLong = raf.readLong();
                long currentSecondLong = raf.readLong();

                if ((currentSecondLong & LONG_2_MSB_MASK) == 0) {
                    break;
                } else {

                    raf.seek(firstFreePointerID * ORIGIN_POINTER_BYTES);
                    raf.writeLong(currentFistLong);
                    raf.writeLong(currentSecondLong & ~LONG_2_MSB_MASK);
                    firstFreePointerID++;
                }
            }

            newId = firstFreePointerID;
            firstFreePointerID++;


            raf.seek(oldId * ORIGIN_POINTER_BYTES);
            raf.writeLong(newId);
            raf.writeLong(LONG_3_MSB_MASK);
        }


        raf.seek(newId * ORIGIN_POINTER_BYTES);
        raf.writeLong(newFirstLong);
        raf.writeLong(newSecondLong);

        return newId;
    }

    public void writeToOriginPointer(byte[] bytes) {
        try {
            originPointerStream.write(bytes);
        } catch (IOException e) {
            logger.error("IOException when writing to the OriginPointer file",e);
            throw new RuntimeException("IOException when writing to the OriginPointer file");
        }

    }

    public long getAndAddFirstFreePointerID() {
        firstFreeDEBUG = firstFreePointerID + 1;
        return firstFreePointerID++;
    }

    public File getOriginPointerFile() {
        return originPointerFile;
    }

    public BufferedOutputStream getOriginPointerStream() {
        return originPointerStream;
    }

    public void flushOriginStream() {
        try {
            originPointerStream.flush();
        } catch (IOException e) {
            logger.error("IOException when flushing the OriginPointer file",e);
            throw new RuntimeException("IOException when flushing the OriginPointer file");
        }

    }

    public static boolean outsourceWasEmpty() {
        return outsourceWasEmpty;
    }
}
