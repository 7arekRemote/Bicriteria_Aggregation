package main;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Settings {
    private static Logger logger = LoggerFactory.getLogger(Settings.class);


    public static Level consoleLogLevel = Level.DEBUG;

    public static boolean saveNewNtds = true;
    public static boolean saveNewMincutGraphs = true;

    public static boolean saveNtdGraphviz = false;

    public static boolean saveSolutions = true;

    public static int threadCount = 1;

    public static boolean dataLog = true;
    public static boolean dataLogHeuristic = false;
    public static boolean dataLogJoinDetailed = false;

    public static boolean outsource = true;
    public static long outsourcedSpaceLimit = 8L * 1024 * 1024 * 1024 * 1024; 
    public static long pruneFreeSpacePuffer = 150L * 1024 * 1024 * 1024; 
    public static boolean deleteOursourcedDataOnExit = true;

    public static boolean ntdOptimizeEfficiencyScore = true; 

    public static boolean deactivatePruning = true; 

    public static boolean useJoinNodeHeuristic = true;

    public static boolean fuseJoinForgetNodes = true;
    public static boolean fuseIntroduceJoinForgetNodes = true; 
    public static boolean forceNoJfSim = false;
    public static boolean forceMedianRoot = false;
    public static boolean sendMailOnExecution = false;
    public static boolean heapDumpOnOOME = true;



    private static File stackOutsourceFolder;
    private static File originPointerOutsourceFolder;
    private static boolean outsourceFolderHasBeenSet = false; 

    public static File getStackOutsourceFolder() {
        return stackOutsourceFolder;
    }
    public static File getOriginPointerOutsourceFolder() {
        return originPointerOutsourceFolder;
    }

    public static void setOutsourceFolder(File stackFolder, File originPointerFolder) {
        if (outsourceFolderHasBeenSet) {
            logger.error("For security reasons, the outsource folder can only be set once.");
        } else {
            outsourceFolderHasBeenSet = true;
            Settings.stackOutsourceFolder = stackFolder;
            Settings.originPointerOutsourceFolder = originPointerFolder;
        }
    }
}
