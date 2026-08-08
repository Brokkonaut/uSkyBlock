package us.talabrek.ultimateskyblock.integrity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public enum IntegrityFiles {;

    public static List<File> listPlayers(File directory) {
        return list(directory, "(?i).+\\.yml");
    }

    public static List<File> listIslands(File directory) {
        return list(directory, "(?i)-?[0-9]+,-?[0-9]+\\.yml");
    }

    public static Collection<File> manifestFiles(File dataFolder, File playerDirectory, File islandDirectory) {
        return manifestFiles(dataFolder, listPlayers(playerDirectory), listIslands(islandDirectory));
    }

    public static Collection<File> manifestFiles(File dataFolder, Collection<File> playerFiles,
                                                 Collection<File> islandFiles) {
        List<File> files = new ArrayList<>();
        files.addAll(playerFiles);
        files.addAll(islandFiles);
        files.addAll(list(new File(dataFolder, "completion"), "(?i).+\\.yml"));
        File orphans = new File(dataFolder, "orphans.yml");
        if (orphans.exists()) {
            files.add(orphans);
        }
        return files;
    }

    private static List<File> list(File directory, String pattern) {
        File[] files = directory.listFiles((dir, name) -> name != null && name.matches(pattern));
        List<File> result = files != null ? new ArrayList<>(Arrays.asList(files)) : new ArrayList<>();
        result.sort(Comparator.comparing(File::getName));
        return result;
    }
}
