package improvements;

import main.Settings;
import multicriteriaSTCuts.dynamicProgamming.algorithms.HeuristicBicritSolutionHeap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class Multithreader {
    static private final Logger logger = LoggerFactory.getLogger(Multithreader.class);

    int freeThreadCount;
    ExecutorService executorService;
    CompletionService<Void> completionService;

    public Multithreader() {
        freeThreadCount = Settings.threadCount;
        executorService = Executors.newFixedThreadPool(Settings.threadCount);
        completionService = new ExecutorCompletionService<>(executorService);
    }

    public void submit(Callable<Void> task) {
        
        try {
            if (freeThreadCount == 0) {
                Future<Void> future = completionService.take();
                freeThreadCount++;

                
                try {
                    future.get();
                } catch (ExecutionException e) {
                    logger.error("Error in the multithreaded task", e.getCause());
                    throw new RuntimeException("Error in the multithreaded task", e.getCause());
                }
            }
        } catch (InterruptedException e) {
            waitForFinish();
            throw new RuntimeException(e);
        }

        freeThreadCount--;

        
        completionService.submit(task);
    }

    public void waitForFinish() {
        executorService.shutdown();
        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException();
        }
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }
}
