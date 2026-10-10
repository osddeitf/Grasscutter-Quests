package emu.grasscutter.data.binout.config;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.TextUtils;
import lombok.val;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** An index of combat config files by their path hash and their path relative to the config folder. */
public final class CombatConfigFolder {
    private final Map<Long, Path> byHash;
    private final Map<String, Path> byName;

    private CombatConfigFolder(Map<Long, Path> byHash, Map<String, Path> byName) {
        this.byHash = byHash;
        this.byName = byName;
    }

    /** Indexes JSON config files under {@code root}, using the named BinOutput directory for path hashes. */
    public static CombatConfigFolder load(Path root, String configPath) {
        Map<Long, Path> byHash = new HashMap<>();
        Map<String, Path> byName = new HashMap<>();
        if (!Files.isDirectory(root)) {
            Grasscutter.getLogger().warn("Combat config folder {} not found", root);
            return new CombatConfigFolder(byHash, byName);
        }
        try (val paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".json") && Files.isRegularFile(path)).forEach(path -> {
                val relative = StreamSupport.stream(root.relativize(path).spliterator(), false)
                    .map(Path::toString).collect(Collectors.joining("/"));
                val name = relative.substring(0, relative.length() - ".json".length());
                byName.put(name, path);
                byHash.put(hashPath(configPath, name), path);
            });
        } catch (IOException e) {
            Grasscutter.getLogger().error("Failed to index combat config folder {}", root, e);
        }
        Grasscutter.getLogger().debug("Indexed {} combat configs in {}", byName.size(), root);
        return new CombatConfigFolder(byHash, byName);
    }

    /**
     * Returns the matching exact hash name, path hash, and short hash name in that order. More than one candidate
     * can be returned because a candidate file may be present but fail to parse.
     */
    public List<Path> findCandidates(long hash) {
        val candidates = new LinkedHashSet<Path>();
        candidates.add(byName.get(Long.toUnsignedString(hash)));
        candidates.add(byHash.get(hash));
        val shortName = String.format("%016x", hash).substring(6, 14);
        candidates.add(byName.get(shortName));
        candidates.remove(null);
        return List.copyOf(candidates);
    }

    private static long hashPath(String configPath, String name) {
        try {
            return TextUtils.getTextHash("Data/_BinOutput/" + configPath + "/" + name + ".MiHoYoBinData");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
