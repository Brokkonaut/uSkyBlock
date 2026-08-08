package us.talabrek.ultimateskyblock.integrity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Append-only human-readable integrity report. */
public class IntegrityReport {
    private final File file;

    public IntegrityReport(File dataFolder, String id) throws IOException {
        File directory = new File(dataFolder, "integrity-reports");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create " + directory);
        }
        file = new File(directory, "integrity-" + id + ".log");
        append("REPORT", "created");
    }

    public synchronized void append(String type, String message) throws IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date());
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(timestamp);
            writer.write(" [");
            writer.write(type);
            writer.write("] ");
            writer.write(message != null ? message.replace('\n', ' ').replace('\r', ' ') : "");
            writer.newLine();
        }
    }

    public File getFile() {
        return file;
    }
}
