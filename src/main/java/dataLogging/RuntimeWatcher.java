package dataLogging;

import com.sun.management.OperatingSystemMXBean;
import main.Settings;
import multicriteriaSTCuts.benchmark.Benchmark;
import multicriteriaSTCuts.dynamicProgamming.outsourcing.OutsourceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.IO;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Path;

public class RuntimeWatcher implements Runnable {
    static private final Logger logger = LoggerFactory.getLogger(RuntimeWatcher.class);

    public static Thread executionThread;

    private static double maxCpuDiff = 0;
    private static int maxHeapMiB = 0;
    private static int maxHeapPercentage = 0;
    private static long maxOutsourceFolderMib = 0;
    private static int maxAllowedHeapMiB;


    private static boolean hasToReset = false;

    @Override
    public void run() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        maxAllowedHeapMiB = (int) (memoryMXBean.getHeapMemoryUsage().getMax() / (1024 * 1024));
        OperatingSystemMXBean osMBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long lastFlush = System.nanoTime();
        long lastOutsourceFolderSize = System.nanoTime();

        while (true) {
            if (Thread.interrupted()) {
                logger.info("Runtime-watcher stopped.");
                return;
            }
            
            if ((System.nanoTime() - lastFlush) >= 1_000_000_000.0 * 60) {
                lastFlush = System.nanoTime();
                DataLog.flush();

            }

            
            double systemCpuLoad = osMBean.getCpuLoad();
            double processCpuLoad = osMBean.getProcessCpuLoad();

            double currCpuDiff = (systemCpuLoad - processCpuLoad) * 100;
            RuntimeWatcher.maxCpuDiff = Math.max(RuntimeWatcher.maxCpuDiff, currCpuDiff);

//            if (currCpuDiff >= 20)
//                logger.warn(String.format("Suspicious Cpu usage outside of this process: %.2f%%", currCpuDiff));

            
            int heapMiB = (int) (memoryMXBean.getHeapMemoryUsage().getUsed() / (1024 * 1024));
            if (heapMiB > maxHeapMiB) {
                maxHeapMiB = heapMiB;
                maxHeapPercentage = (int) ((float) maxHeapMiB / (float) maxAllowedHeapMiB*100);
            }



            
            if (Benchmark.currentResult != null && Benchmark.currentResult.startTime != -1) {
                long startTime = Benchmark.currentResult.startTime;
                long currentTime = System.nanoTime();
                long elapsedMs = (long) ((currentTime - startTime) / 1_000_000.0);

                long[] stackFolderData = IO.getFolderSize(Path.of(Settings.getStackOutsourceFolder().toString()));
                long[] originPointerFileData = IO.getFolderSize(Path.of(Settings.getOriginPointerOutsourceFolder().toString()));


                long stackFolderMib = stackFolderData[0] / (1024 * 1024);
                long stackFolderFileCount = stackFolderData[1];
                long stackFolderMeasureMs = (long) (stackFolderData[2] / 1_000_000.0);

                long originPointerFileMib = originPointerFileData[0] / (1024 * 1024);
                long originPointerFileFileCount = originPointerFileData[1];
                long originPointerFileMeasureMs = (long) (originPointerFileData[2] / 1_000_000.0);

                long outsourceFolderFileCount = stackFolderFileCount + originPointerFileFileCount;
                long outsourceFolderMeasureMs = stackFolderMeasureMs + originPointerFileMeasureMs;
                long outsourceFolderMib = stackFolderMib + originPointerFileMib;

                DataLog.space(String.format("%d,%d,%d,%d,%d,%d,%d,%d,%d,%d",
                        Benchmark.currentResult.graph_id,Benchmark.currentResult.ntd_nr,
                        elapsedMs,
                        heapMiB,
                        outsourceFolderMib, stackFolderMib, originPointerFileMib,
                        outsourceFolderFileCount,
                        outsourceFolderMeasureMs, OutsourceHandler.firstFreeDEBUG));

                if (outsourceFolderMib > maxOutsourceFolderMib) {
                    maxOutsourceFolderMib = outsourceFolderMib;
                }

                
                if(Settings.outsource && outsourceFolderMib > Settings.outsourcedSpaceLimit / (1024 * 1024)){
                    logger.error("Outsource folder too large. Program is terminated.");
                    DataLog.close();
                    System.exit(6843); 
                }

            }

            
            if (hasToReset) {
                maxCpuDiff = 0;
                maxHeapMiB = 0;
                maxHeapPercentage = 0;
                maxOutsourceFolderMib = 0;
                hasToReset = false;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.info("Runtime-watcher stopped.");
                return;
            }

        }
    }

    public static void resetStatistics() {
        hasToReset = true;
    }

    public static double getMaxCpuDiff() {
        if (hasToReset) return 0;
        return maxCpuDiff;
    }

    public static int getMaxHeapMiB() {
        if (hasToReset) return 0;
        return maxHeapMiB;
    }
    public static int getMaxHeapPercentage() {
        if (hasToReset) return 0;
        return maxHeapPercentage;
    }

    public static long getMaxOutsourceFolderMib() {
        if (hasToReset) return 0;
        return maxOutsourceFolderMib;
    }

    public static void start() {
        if (executionThread == null || !executionThread.isAlive()) {
            executionThread = new Thread(new RuntimeWatcher());
            executionThread.start();
        }
    }


    public static void stop() {
        if (executionThread != null && executionThread.isAlive()) {
            logger.info("Stopping Runtime-watcher thread");
            executionThread.interrupt();
        }
    }
}
