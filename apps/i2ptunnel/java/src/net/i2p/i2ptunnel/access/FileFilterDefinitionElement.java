package net.i2p.i2ptunnel.access;

import java.util.HashMap;
import java.util.Map;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import net.i2p.I2PAppContext;
import net.i2p.data.Hash;
import net.i2p.util.Log;

/**
 * An element of filter definition that reads hashes of remote destinations
 * from a file.
 *
 * @since 0.9.40
 */
class FileFilterDefinitionElement extends FilterDefinitionElement {

    private final File file;
    private final Map<Hash, DestTracker> lastLoaded = new HashMap<>();
    private volatile long lastLoading;

    /**
     * @param file file to read the remote destinations from
     * @param threshold threshold to apply to all those destinations
     */
    FileFilterDefinitionElement(File file, Threshold threshold) {
        super(threshold);
        this.file = file;
    }

    @Override
    public void update(Map<Hash, DestTracker> map) throws IOException {
        if (!(file.exists() && file.isFile()))
            return;
        if (file.lastModified() <= lastLoading) {
            synchronized (lastLoaded) {
                for (Map.Entry<Hash, DestTracker> entry : lastLoaded.entrySet()) {
                    if (!map.containsKey(entry.getKey()))
                        map.put(entry.getKey(),entry.getValue());
                }
            }
            return;
        }

        lastLoading = System.currentTimeMillis();
        synchronized (lastLoaded) {
            lastLoaded.clear();
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String b32;
            while((b32 = reader.readLine()) != null) {
                if (b32.length() == 0)
                    continue;
                Hash hash;
                try {
                    hash = fromBase32(b32);
                } catch (InvalidDefinitionException bad32) {
                    Log log = I2PAppContext.getGlobalContext().logManager().getLog(FileFilterDefinitionElement.class);
                    log.error("Invalid access list entry \"" + b32 + "\" in " + file, bad32);
                    continue;
                }
                if (map.containsKey(hash))
                    continue;
                DestTracker newTracker = new DestTracker(hash, threshold);
                map.put(hash, newTracker);
                synchronized (lastLoaded) {
                    lastLoaded.put(hash, newTracker);
                }
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException ignored) {}
            }
        }
    }
}
