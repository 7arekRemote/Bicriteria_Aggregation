package main;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import dataLogging.DataLog;
import dataLogging.RuntimeWatcher;
import multicriteriaSTCuts.benchmark.Benchmark;
import multicriteriaSTCuts.benchmark.JoinDetailed;
import multicriteriaSTCuts.dynamicProgamming.outsourcing.OutsourceHandler;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static multicriteriaSTCuts.MincutSolver.DecomposerKind.HEURISTIC_Fast;

public class Main {
    static private final org.slf4j.Logger logger = LoggerFactory.getLogger(Main.class);

    private static boolean isShutdown = false; 

    public static int wrongSolutionAppeared = -1; 



    public static String sessionOutputFolder = "";

    public static void main(String[] args){
        
        Map<String,String> argMap = parseArguments(args);
        
        if (argMap.isEmpty()) {
            Sandbox.settings();
        }

        initSessionOutputFolder();
        
        initLogback();

        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        logger.info("Date: {}",dtf.format(now));

        double maxHeapGiB = Math.round(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / (1024f * 1024 * 1024)*10)/10f;
        logger.info("Max. heap memory in GiB: {}",maxHeapGiB);

        logger.info("Max. number of threads: {}",Settings.threadCount);

        try {
            DataLog.init();
        } catch (IOException e) {
            logger.error("DataLogs could not be initialized",e);
            throw new RuntimeException(e);
        }

        RuntimeWatcher.start();

        Thread hook = new Thread(() -> {
            logger.info("Shutdown-hook was called...");
            shutdown();
            logger.info("Shutdown hook finished.");
            if(wrongSolutionAppeared == 1) {
                logger.error("AN INCORRECT SOLUTION HAS APPEARED IN THE CORRECTNESS TESTER!");
                logger.error("AN INCORRECT SOLUTION HAS APPEARED IN THE CORRECTNESS TESTER!");
                logger.error("AN INCORRECT SOLUTION HAS APPEARED IN THE CORRECTNESS TESTER!");
            } else if(wrongSolutionAppeared == 0) {
                logger.info("NO incorrect solution occurred in the CorrectnessTester.");
            }
        });
        Runtime.getRuntime().addShutdownHook(hook);

        if (!argMap.isEmpty()) {
            File stackFolder = new File(argMap.getOrDefault("originPointerFolder","./mincut_outsourced_space/sp"));
            File originPointerFolder = new File(argMap.getOrDefault("surfacePointerFolder","./mincut_outsourced_space/op"));

            Settings.setOutsourceFolder(stackFolder, originPointerFolder);
        }


        OutsourceHandler.initStackFolder();

        try {
            if (!argMap.isEmpty()) {
                logger.info("Arguments were specified. Ignoring Sandbox.run().");
                for (Map.Entry<String, String> entry : argMap.entrySet()) {
                    logger.info("Argument {}: {}", entry.getKey(), entry.getValue());
                }
                String parsedDataset = argMap.get("dataset");
                String datasetsFolder = parsedDataset.substring(0, parsedDataset.lastIndexOf("/"));
                String datasetName = parsedDataset.substring(parsedDataset.lastIndexOf("/")+1);

                boolean useGivenTD = Boolean.parseBoolean(argMap.getOrDefault("useGivenTD","true"));

                if (argMap.containsKey("benchmarkSampledEntries") && argMap.get("benchmarkSampledEntries").equals("true")) {
                    JoinDetailed.benchmarkSampledEntries("saved_output/joinDetailed/" + datasetName,true);
                } else {
                    Benchmark.benchmarkDataset(datasetsFolder, datasetName, 1, true, HEURISTIC_Fast, true, Benchmark.BenchmarkMode.STANDARD, 10, 10, useGivenTD, 0);
                }


            } else {
                logger.info("No arguments were specified. Executing Sandbox.run()...");
                Sandbox.run();
            }
        } catch (Throwable t) {
            logger.error("An Exception occurred",t);
            shutdown();
            throw new RuntimeException(t);
        }

        logger.info("Code run through completely");

        shutdown();
    }

    private static Map<String,String> parseArguments(String[] args) {
        Map<String,String> argMap = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help":
                    logger.info("Options:");
                    logger.info("  --help                   Show this help message.");
                    logger.info("  --dataset <path>         Specify the dataset to use (required).");
                    logger.info("  --threads <number>       Set the number of threads. Default is 1.");
                    logger.info("  --outsource <boolean>    Set the outsource option. Default is true.");
                    logger.info("  --originPointerFolder <path>    Set the folder for the outsourced originPointers. Default is \"./mincut_outsourced_space/sp\".");
                    logger.info("  --surfacePointerFolder <path>    Set the folder for the outsourced surfacePointers. Default is \"./mincut_outsourced_space/op\".");
                    logger.info("  --useGivenTD <boolean>          Use the existing tree decomposition (or create a new one on the spot). Default is true.");
                    logger.info("  --enablePruning <boolean>          Set the pruning option. Default is false.");
                    logger.info("  --useJoinNodeHeuristic <boolean>          Set the join heuristic option. Default is true.");
                    logger.info("  --jf <boolean>          Set the fuse join-forget nodes option. Default is true.");
                    logger.info("  --ijf <boolean>          Set the fuse introduce-join-forget nodes option. Only works with jf and outsource activated. Default is true");
                    logger.info("  --outsourcedSpaceLimit <number>[K,M,G,T] Set the outsourced space limit (in bytes). Default is 8T.");
                    System.exit(1); 

                case "--dataset":
                    if (i + 1 < args.length) {
                        argMap.put("dataset", args[++i]); 
                    } else {
                        logger.error("Error: --dataset requires a value");
                        System.exit(48);
                    }
                    break;

                case "--threads":
                    if (i + 1 < args.length) {
                        argMap.put("threads", args[++i]); 
                        Settings.threadCount = Integer.parseInt(argMap.get("threads"));
                    } else {
                        logger.error("Error: --threads requires a value");
                        System.exit(48);
                    }
                    break;

                case "--outsource":
                    if (i + 1 < args.length) {
                        argMap.put("outsource", args[++i]); 
                        Settings.outsource = Boolean.parseBoolean(argMap.get("outsource"));
                    } else {
                        logger.error("Error: --outsource requires a value");
                        System.exit(48);
                    }
                    break;

             case "--useGivenTD":
                    if (i + 1 < args.length) {
                        argMap.put("useGivenTD", args[++i]); 
                    } else {
                        logger.error("Error: --useGivenTD requires a value");
                        System.exit(48);
                    }
                    break;

                case "--originPointerFolder":
                    if (i + 1 < args.length) {
                        argMap.put("originPointerFolder", args[++i]); 
                    } else {
                        logger.error("Error: --originPointerFolder requires a value");
                        System.exit(48);
                    }
                    break;

                case "--surfacePointerFolder":
                    if (i + 1 < args.length) {
                        argMap.put("surfacePointerFolder", args[++i]); 
                    } else {
                        logger.error("Error: --surfacePointerFolder requires a value");
                        System.exit(48);
                    }
                    break;

                case "--jf":
                    if (i + 1 < args.length) {
                        argMap.put("jf", args[++i]); 
                        Settings.fuseJoinForgetNodes = Boolean.parseBoolean(argMap.get("jf"));
                    } else {
                        logger.error("Error: --jf requires a value");
                        System.exit(48);
                    }
                    break;
                case "--ijf":
                    if (i + 1 < args.length) {
                        argMap.put("ijf", args[++i]); 
                        Settings.fuseIntroduceJoinForgetNodes = Boolean.parseBoolean(argMap.get("ijf"));
                    } else {
                        logger.error("Error: --ijf requires a value");
                        System.exit(48);
                    }
                    break;
                case "--outsourcedSpaceLimit":
                    if (i + 1 < args.length) {
                        argMap.put("outsourcedSpaceLimit", args[++i]); 
                        Settings.outsourcedSpaceLimit = parseSpaceArg(argMap.get("outsourcedSpaceLimit"));
                    } else {
                        logger.error("Error: --outsourcedSpaceLimit requires a value");
                        System.exit(48);
                    }
                    break;
                case "--forceMedianRoot":
                    if (i + 1 < args.length) {
                        argMap.put("forceMedianRoot", args[++i]); 
                        Settings.forceMedianRoot = Boolean.parseBoolean(argMap.get("forceMedianRoot"));
                    } else {
                        logger.error("Error: --forceMedianRoot requires a value");
                        System.exit(48);
                    }
                    break;
                case "--enablePruning":
                    if (i + 1 < args.length) {
                        argMap.put("enablePruning", args[++i]); 
                        Settings.deactivatePruning = ! Boolean.parseBoolean(argMap.get("enablePruning"));
                    } else {
                        logger.error("Error: --enablePruning requires a value");
                        System.exit(48);
                    }
                    break;
                case "--dataLogJoinDetailed":
                    if (i + 1 < args.length) {
                        argMap.put("dataLogJoinDetailed", args[++i]); 
                        Settings.dataLogJoinDetailed = Boolean.parseBoolean(argMap.get("dataLogJoinDetailed"));
                    } else {
                        logger.error("Error: --dataLogJoinDetailed requires a value");
                        System.exit(48);
                    }
                    break;
                case "--ntdOptimizeEfficiencyScore":
                    if (i + 1 < args.length) {
                        argMap.put("ntdOptimizeEfficiencyScore", args[++i]); 
                        Settings.ntdOptimizeEfficiencyScore = Boolean.parseBoolean(argMap.get("ntdOptimizeEfficiencyScore"));
                    } else {
                        logger.error("Error: --ntdOptimizeEfficiencyScore requires a value");
                        System.exit(48);
                    }
                    break;
                case "--forceNoJfSim":
                    if (i + 1 < args.length) {
                        argMap.put("forceNoJfSim", args[++i]); 
                        Settings.forceNoJfSim = Boolean.parseBoolean(argMap.get("forceNoJfSim"));
                    } else {
                        logger.error("Error: --forceNoJfSim requires a value");
                        System.exit(48);
                    }
                    break;
                case "--useJoinNodeHeuristic":
                    if (i + 1 < args.length) {
                        argMap.put("useJoinNodeHeuristic", args[++i]); 
                        Settings.useJoinNodeHeuristic = Boolean.parseBoolean(argMap.get("useJoinNodeHeuristic"));
                    } else {
                        logger.error("Error: --useJoinNodeHeuristic requires a value");
                        System.exit(48);
                    }
                    break;
                case "--benchmarkSampledEntries":
                    if (i + 1 < args.length) {
                        argMap.put("benchmarkSampledEntries", args[++i]); 
                    } else {
                        logger.error("Error: --benchmarkSampledEntries requires a value");
                        System.exit(48);
                    }
                    break;
                default:
                    System.err.println("Unknown argument: " + arg);
                    break;
            }
        }
        return argMap;
    }

    private static long parseSpaceArg(String value) {
        long multiplier = 1;
        if (value.toUpperCase().endsWith("K")) {
            multiplier = 1024;
            value = value.substring(0, value.length() - 1);
        } else if (value.toUpperCase().endsWith("M")) {
            multiplier = 1024 * 1024;
            value = value.substring(0, value.length() - 1);
        } else if (value.toUpperCase().endsWith("G")) {
            multiplier = 1024 * 1024 * 1024;
            value = value.substring(0, value.length() - 1);
        } else if (value.toUpperCase().endsWith("T")) {
            multiplier = 1024 * 1024 * 1024 * 1024L;
            value = value.substring(0, value.length() - 1);
        }
        return Long.parseLong(value) * multiplier;
    }

    private static void initSessionOutputFolder() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String folderName = dateFormat.format(new Date());
        sessionOutputFolder = "output/" + folderName;
        File outputFolder = new File(sessionOutputFolder);
        outputFolder.mkdirs();
    }

    private static void initLogback() {
        
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.TRACE);
        rootLogger.detachAndStopAllAppenders();



        ConsoleAppender consoleAppender = new ConsoleAppender();
        consoleAppender.setContext(loggerContext);
        consoleAppender.setName("STDOUT");

        
        ThresholdFilter thresholdFilter = new ThresholdFilter();
        thresholdFilter.setLevel(Settings.consoleLogLevel.levelStr);
        thresholdFilter.start();
        consoleAppender.addFilter(thresholdFilter);

        PatternLayoutEncoder consoleEncoder = new PatternLayoutEncoder();
        consoleEncoder.setContext(loggerContext);
        consoleEncoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %class{0} - %msg%n");
        consoleEncoder.start();
        consoleAppender.setEncoder(consoleEncoder);
        consoleAppender.start();
        rootLogger.addAppender(consoleAppender);

        
        FileAppender fileAppender = new FileAppender();
        fileAppender.setContext(loggerContext);
        fileAppender.setName("FILE");
        fileAppender.setFile("output/logs/logfile.log");
        PatternLayoutEncoder fileEncoder = new PatternLayoutEncoder();
        fileEncoder.setContext(loggerContext);
        fileEncoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %class{0} - %msg%n");
        fileAppender.setFile(sessionOutputFolder + "/application.log");
        fileEncoder.start();
        fileAppender.setEncoder(fileEncoder);
        fileAppender.start();
        rootLogger.addAppender(fileAppender);
    }

    private static void shutdown() {
        if(isShutdown) return;
        isShutdown = true;

        DataLog.close();

        if (Benchmark.currentBenchmarkThread != null) {
            Benchmark.currentBenchmarkThread.interrupt();
            try {
                Benchmark.currentBenchmarkThread.join(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        if(Settings.deleteOursourcedDataOnExit && OutsourceHandler.outsourceWasEmpty()) {
            OutsourceHandler.deleteOutsourcedData();
        }

        RuntimeWatcher.stop();
    }
}