package multicriteriaSTCuts.benchmark;

import dataLogging.RuntimeWatcher;
import improvements.Multithreader;
import main.Settings;
import multicriteriaSTCuts.dynamicProgamming.MincutSolutionVector;
import multicriteriaSTCuts.dynamicProgamming.SolutionPointer;
import multicriteriaSTCuts.dynamicProgamming.algorithms.BicritSolutionHeap;
import multicriteriaSTCuts.dynamicProgamming.algorithms.FusedBicritSolutionHeap;
import multicriteriaSTCuts.dynamicProgamming.algorithms.HeuristicBicritSolutionHeap;
import multicriteriaSTCuts.dynamicProgamming.algorithms.NormalBicritSolutionHeap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class JoinDetailed {
    static private final Logger logger = LoggerFactory.getLogger(JoinDetailed.class);

    static private final int SURFACE_POINTER_BYTES = Long.BYTES + 2 * Double.BYTES;

    static private final boolean uniqueWeights = true;

    public static long benchmarkSampledEntries(String joinDetailedFolder,boolean useLogging) {
        File jdFolder = new File(joinDetailedFolder);
        
        String datasetName = jdFolder.getName();

        
        Benchmark.current_graph_id++;
        Benchmark.current_ntd_nr = 0;
        Benchmark.currentResult = new Result();
        Benchmark.currentResult.graph_id = Benchmark.current_graph_id;
        Benchmark.currentResult.ntd_nr = Benchmark.current_ntd_nr;
        RuntimeWatcher.resetStatistics();

        
        AtomicLong totalExtrepolatedTime = new AtomicLong(0);
        AtomicLong totalSampledTime = new AtomicLong(0);

        
        ArrayList<Integer> nodeNrs = Arrays.stream(jdFolder.list())
                .map(Integer::parseInt)
                .collect(Collectors.toCollection(ArrayList::new));

        nodeNrs.sort(Comparator.comparingInt(Integer::intValue));


        for (Integer nodeNr : nodeNrs) {

            File nodeFolder = new File(jdFolder + "/" + nodeNr);

            
            Map<String, String> map = parseInfoTxt(nodeFolder);
            int bag_size = Integer.parseInt(map.get("bag_size"));
            int forgotten_size = Integer.parseInt(map.get("forgotten_size"));

            
            ArrayList<Integer> entryIndices = Arrays.stream(nodeFolder.listFiles(File::isDirectory))
                    .map(File::getName)             
                    .map(Integer::parseInt)        
                    .collect(Collectors.toCollection(ArrayList::new));

            entryIndices.sort(Comparator.comparingInt(Integer::intValue));


            long startTime = System.nanoTime();
            if (forgotten_size == 0) {
                

                Multithreader multithreader = new Multithreader();
                for (Integer entryIndex : entryIndices) {
                    if(Thread.interrupted()){
                        multithreader.waitForFinish();
                        throw new RuntimeException();
                    }
                    multithreader.submit(() -> {
                        joinComputeEntry(nodeNr, entryIndex, nodeFolder);
                        return null;
                    });
                }
                multithreader.waitForFinish();

            } else {
                if (Settings.fuseIntroduceJoinForgetNodes == false) {
                    

                    forgotten_size = 0; 
                    entryIndices = Arrays.stream(nodeFolder.listFiles(File::isDirectory)) 
                            .flatMap(subFolder -> Arrays.stream(subFolder.listFiles(File::isDirectory))) 
                            .map(File::getName)             
                            .map(Integer::parseInt)         
                            .collect(Collectors.toCollection(ArrayList::new));

                    List<String> filePaths = Arrays.stream(nodeFolder.listFiles(File::isDirectory)) 
                            .flatMap(subFolder -> Arrays.stream(subFolder.listFiles(File::isDirectory))) 
                            .map(File::getAbsolutePath) 
                            .collect(Collectors.toList());

                    Multithreader multithreader = new Multithreader();
                    for (String path : filePaths) {
                        if(Thread.interrupted()){
                            multithreader.waitForFinish();
                            throw new RuntimeException();
                        }
                        multithreader.submit(() -> {
                            File entryFolder = new File(path);
                            int entryIndex = Integer.parseInt(entryFolder.getName());
                            joinComputeEntry(new File(path),nodeNr,entryIndex);
                            return null;
                        });
                    }
                    multithreader.waitForFinish();

                } else {
                    
                    Multithreader multithreader = new Multithreader();
                    for (Integer entryIndex : entryIndices) {
                        if(Thread.interrupted()){
                            multithreader.waitForFinish();
                            throw new RuntimeException();
                        }
                        multithreader.submit(() -> {
                            joinForgetComputeEntry(nodeNr, entryIndex, nodeFolder);
                            return null;
                        });
                    }
                    multithreader.waitForFinish();
                }

            }
            long endTime = System.nanoTime();
            long sampledTime = (endTime - startTime) / 1_000_000;

            int entrySampleCount = entryIndices.size();
            int entryFullCount = (int) Math.pow(2, bag_size-forgotten_size);

            long extrapolatedTime = (long) (sampledTime * (entryFullCount / (double) entrySampleCount));
            totalExtrepolatedTime.addAndGet(extrapolatedTime);
            totalSampledTime.addAndGet(sampledTime);
            if(extrapolatedTime > 1000)
                if(useLogging) logger.trace("Node " + nodeNr + " : " + sampledTime + " -> " + extrapolatedTime + " ms");
        }

        if(useLogging) logger.info("Dataset " + datasetName + " took " + totalSampledTime + " ms, extrapolated time: " + totalExtrepolatedTime + " ms (" + totalExtrepolatedTime.get()/1000/60/60f + " h)");
        return totalExtrepolatedTime.get();
    }

    private static void joinComputeEntry(Integer nodeNr, Integer entryIndex, File nodeFolder) {
        File entryFolder = new File(nodeFolder + "/" + entryIndex);
        ArrayList<SolutionPointer> aSolutions = getSolutionsFromFile(new File(entryFolder + "/first.data"));
        ArrayList<SolutionPointer> bSolutions = getSolutionsFromFile(new File(entryFolder + "/second.data"));

        MincutSolutionVector.poCombineBicrit(aSolutions, bSolutions, new double[2], nodeNr,entryIndex,uniqueWeights);
    }
    private static void joinComputeEntry(File entryFolder, Integer nodeNr, Integer entryIndex) {
        

        ArrayList<SolutionPointer> aSolutions = getSolutionsFromFile(new File(entryFolder + "/first.data"));
        ArrayList<SolutionPointer> bSolutions = getSolutionsFromFile(new File(entryFolder + "/second.data"));

        MincutSolutionVector.poCombineBicrit(aSolutions, bSolutions, new double[2], nodeNr,entryIndex,uniqueWeights);
    }

    private static void joinForgetComputeEntry(Integer nodeNr, Integer mainEntryIndex, File nodeFolder) {
        File mainEntryFolder = new File(nodeFolder + "/" + mainEntryIndex);
        double[] weightOverlap = new double[2];

        List<MincutSolutionVector.HeapTuple> heapTupleList = new ArrayList<>();

        
        ArrayList<Integer> otherEntryIndices = Arrays.stream(mainEntryFolder.listFiles(File::isDirectory))
                .map(File::getName)             
                .map(Integer::parseInt)        
                .collect(Collectors.toCollection(ArrayList::new));

        otherEntryIndices.sort(Comparator.comparingInt(Integer::intValue));

        for (Integer otherIndex : otherEntryIndices) {

            File otherIndexFolder = new File(mainEntryFolder + "/" + otherIndex);

            ArrayList<SolutionPointer> other_aSolutions = getSolutionsFromFile(new File(otherIndexFolder + "/first.data"));
            ArrayList<SolutionPointer> other_bSolutions = getSolutionsFromFile(new File(otherIndexFolder + "/second.data"));

            heapTupleList.add(new MincutSolutionVector.HeapTuple(other_aSolutions, other_bSolutions, weightOverlap,otherIndex));
        }

        MincutSolutionVector.poCombineFusedBicrit(heapTupleList, nodeNr, mainEntryIndex, uniqueWeights);
    }

    private static ArrayList<SolutionPointer> getSolutionsFromFile(File entryFile) {


        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(entryFile))){
            ArrayList<SolutionPointer> solutionPointers = new ArrayList<>();

            
            while (true) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(SURFACE_POINTER_BYTES);
                if (inputStream.read(byteBuffer.array()) != SURFACE_POINTER_BYTES)
                    break;

                solutionPointers.add(getSurfacePointerFromBytes(byteBuffer));
            }
            return solutionPointers;

        } catch (FileNotFoundException e) {
            logger.error("get() may only be called for existing (i.e. != null) entries!",e);
            throw new RuntimeException("get() may only be called for existing (i.e. != null) entries!");
        } catch (IOException e) {
            logger.error("The read in file " + entryFile.getAbsolutePath() + " has thrown an IOExeption",e);
            throw new RuntimeException("The read in file " + entryFile.getAbsolutePath() + " has thrown an IOExeption");
        }
    }
    private static SolutionPointer getSurfacePointerFromBytes(ByteBuffer byteBuffer) {
        
        long id = byteBuffer.getLong();
        double weight[] = new double[2];
        for (int i = 0; i < 2; i++) {
            weight[i] = byteBuffer.getDouble();
        }
        
        return new SolutionPointer(weight, id);
    }

    private static Map<String, String> parseInfoTxt(File nodeFolder) {
        File infoFile = new File(nodeFolder + "/info.txt");
        Map<String, String> map = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(infoFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                
                String[] parts = line.split(" ");
                if (parts.length == 2) {
                    String key = parts[0];
                    String value = parts[1];
                    map.put(key, value);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }
}
