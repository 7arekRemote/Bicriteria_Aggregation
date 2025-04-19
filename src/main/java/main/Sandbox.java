package main;


import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;


public class Sandbox {
    static private final Logger logger = LoggerFactory.getLogger(Sandbox.class);

    public static void settings() {
        Settings.threadCount = 4;

        Settings.consoleLogLevel = Level.TRACE;

        Settings.setOutsourceFolder(new File("./mincut_outsourced_space/sp"),new File("./mincut_outsourced_space/op"));

    }


    public static void run() throws InterruptedException {
        //Custom code here

    }

}
