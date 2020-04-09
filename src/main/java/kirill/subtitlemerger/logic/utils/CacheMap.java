package kirill.subtitlemerger.logic.utils;

import lombok.AllArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class is a very simple but helpful cache implementation. It stores items eternally unless the number of the
 * items exceeds maxSize in which case the eldest item is evicted.
 */
@AllArgsConstructor
public class CacheMap<K,V> extends LinkedHashMap<K, V> {
    private int maxSize;

    @Override
    protected boolean removeEldestEntry(Map.Entry eldest) {
        return size() > maxSize;
    }
}
