package improvements;

import main.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Mailer {
    private static Logger logger = LoggerFactory.getLogger(Mailer.class);

    public static void sendMail(String title, String text) {
        logger.info("The mailer has been deactivated in the current version.");

//        try {
//            String pythonScriptPath = "src/main/python/mail.py";
//
////            String command = "python3 " + pythonScriptPath + " \"" + title + "\" \"" + text + "\"";
//            String[] command = {"python3", pythonScriptPath, title, text};
//
//            Process process = Runtime.getRuntime().exec(command);
//
//            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//            String line;
//            while ((line = reader.readLine()) != null) {
//                logger.info(line);
//            }
//
//            process.waitFor();
//           logger.info("Python-Skript beendet mit Exit-Code " + process.exitValue());
//
//        } catch (IOException | InterruptedException e) {
//            logger.error("Mail Exception: ",e);
//        }
    }
}
