package utils;

import main.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;

public class IO {
    static private final Logger logger = LoggerFactory.getLogger(IO.class);
    public static synchronized File getFreeFileName(String format, Object... args) {
        String filePath = String.format(format, args);
        try {
            File file = new File(filePath);
            int i = 0;
            int lastDotIndex = filePath.lastIndexOf('.');
            String fileName = filePath.substring(0, lastDotIndex);
            new File(file.getParent()).mkdirs();
            String fileExtension = filePath.substring(lastDotIndex);
            while (true) {
                file = new File(fileName + "(" + i + ")" + fileExtension);
                if (file.createNewFile())
                    return file;
                i++;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static synchronized File getFreeFolderName(String format, Object... args) {
        String filePath = String.format(format, args);
        File file = new File(filePath);
        int i = 0;
        new File(file.getParent()).mkdirs();
        while (true) {
            file = new File(filePath + "(" + i + ")");
            if (file.mkdir())
                return file;
            i++;
        }
    }

    public static void tryCreateFile(File file,boolean exeptionOnFailure) {
        boolean operationSuccesfull = false;
        if (!file.exists()) {
            try {
                operationSuccesfull = file.createNewFile();
            } catch (IOException e) {
                logger.error("The creation of File " + file.getAbsolutePath() + " has thrown an IOExeption",e);
                throw new RuntimeException("The creation of File " + file.getAbsolutePath() + " has thrown an IOExeption");
            }
        }
        if(exeptionOnFailure && !operationSuccesfull)
            throw new RuntimeException("The creation of File " + file.getAbsolutePath() + " did not work.");
    }

    public static void deleteDir(File file,boolean keepFolder) {
        if (file == null) return;
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f,false);
            }
        }
        
        if (file.getAbsolutePath().startsWith(Settings.getStackOutsourceFolder().getAbsolutePath()) ||
                file.getAbsolutePath().startsWith(Settings.getOriginPointerOutsourceFolder().getAbsolutePath())) {
            if (!keepFolder) {
                file.delete();
            }
        } else {
            logger.error("Data deletion protection has detected an incorrect path and has not deleted anything.");
            System.exit(94);
        }
    }

    public static File addFolderSuffix(File file, String suffix, boolean exeptionOnFailure) {
        File renamedFile = new File(file.getAbsolutePath() + suffix);
        boolean operationSuccess = file.renameTo(renamedFile);
        if (!operationSuccess && exeptionOnFailure)
            throw new RuntimeException("An array folder could not be renamed.");
        return renamedFile;
    }

    public static long[] getFolderSize(Path path) {

        long startTime = System.nanoTime();
        final AtomicLong size = new AtomicLong(0);
        final AtomicLong fileCount = new AtomicLong(0);

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

                    size.addAndGet(attrs.size());
                    fileCount.addAndGet(1);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {


                    size.set(-1);
                    fileCount.set(-1);
                    return FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {

                    if (exc != null) {

                        size.set(-1);
                        fileCount.set(-1);
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new AssertionError("walkFileTree will not throw IOException if the FileVisitor does not");
        }

        long endTime = System.nanoTime();

        return new long[]{size.get(), fileCount.get(),endTime-startTime};
    }
}
