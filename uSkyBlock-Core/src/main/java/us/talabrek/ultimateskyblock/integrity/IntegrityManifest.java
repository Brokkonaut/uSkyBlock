package us.talabrek.ultimateskyblock.integrity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** SHA-256 manifest used to reject a stale repair plan. */
public class IntegrityManifest {
    private final File root;
    private final Map<String, String> hashes;

    private IntegrityManifest(File root, Map<String, String> hashes) {
        this.root = root;
        this.hashes = Collections.unmodifiableMap(new LinkedHashMap<>(hashes));
    }

    public static IntegrityManifest capture(File root, Collection<File> files) throws IOException {
        List<File> ordered = new ArrayList<>(files);
        ordered.sort(Comparator.comparing(file -> path(root, file)));
        Map<String, String> hashes = new LinkedHashMap<>();
        for (File file : ordered) {
            if (file.exists() && file.isFile()) {
                hashes.put(path(root, file), hash(file));
            }
        }
        return new IntegrityManifest(root, hashes);
    }

    public boolean matches(Collection<File> files) throws IOException {
        return hashes.equals(capture(root, files).hashes);
    }

    boolean hasSameFileList(Collection<File> files) {
        Set<String> current = new LinkedHashSet<>();
        for (File file : files) {
            if (file.exists() && file.isFile()) {
                current.add(path(root, file));
            }
        }
        return hashes.keySet().equals(current);
    }

    boolean matchesFile(File file) throws IOException {
        String expected = hashes.get(path(root, file));
        return expected != null && file.exists() && expected.equals(hash(file));
    }

    public Map<String, String> getHashes() {
        return hashes;
    }

    static IntegrityManifest fromHashes(File root, Map<String, String> hashes) {
        return new IntegrityManifest(root, hashes);
    }

    static String path(File root, File file) {
        return root.toPath().toAbsolutePath().normalize().relativize(
                file.toPath().toAbsolutePath().normalize()).toString().replace(File.separatorChar, '/');
    }

    static String hash(File file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
