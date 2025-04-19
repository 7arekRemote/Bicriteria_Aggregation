package utils;

import com.sun.management.HotSpotDiagnosticMXBean;
import multicriteriaSTCuts.benchmark.Benchmark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.io.IOException;

public class Diagnostics {
    
    private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";
    private static HotSpotDiagnosticMXBean hotspotMBean;
    private static final Logger logger = LoggerFactory.getLogger(Diagnostics.class);

    public static void createHeapDump(String folderPath){
        logger.info("Creating heap dump...");
        long elapsedMs = (long) ((System.nanoTime() - Benchmark.currentResult.startTime) / 1_000_000.0);
        folderPath += "/" + elapsedMs;

        new File(folderPath).mkdirs();

        if (hotspotMBean == null) {
            try {
                hotspotMBean = ManagementFactory.newPlatformMXBeanProxy(
                        ManagementFactory.getPlatformMBeanServer(),
                        HOTSPOT_BEAN_NAME,
                        HotSpotDiagnosticMXBean.class);
                String liveFilePath = folderPath + "/heapDump_live.hprof";
                String notLiveFilePath = folderPath + "/heapDump_notlive.hprof";

                hotspotMBean.dumpHeap(liveFilePath, true);
                hotspotMBean.dumpHeap(notLiveFilePath, false);
            } catch (IOException e) {
                logger.error("Error while creating heap dump", e);
            }
        }


    }
}
