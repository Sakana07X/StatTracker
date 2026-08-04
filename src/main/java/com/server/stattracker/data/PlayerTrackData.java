package com.server.stattracker.data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTrackData {

    private final ConcurrentHashMap<String, Long>   counters = new ConcurrentHashMap<>(16);
    private final ConcurrentHashMap<String, Set<String>> sets = new ConcurrentHashMap<>(8);
    private final ConcurrentHashMap<String, Double> doubles  = new ConcurrentHashMap<>(8);

    transient volatile boolean dirty = false;

    public long getCounter(String key) {
        Long val = counters.get(key);
        return val != null ? val : 0L;
    }

    public long increment(String key) {
        return counters.merge(key, 1L, Long::sum);
    }

    public long increment(String key, long amount) {
        return counters.merge(key, amount, Long::sum);
    }

    public void setCounter(String key, long value) {
        counters.put(key, value);
    }

    private Set<String> getSet(String key) {
        return sets.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
    }

    public boolean addToSet(String key, String value) {
        return getSet(key).add(value);
    }

    public int getSetSize(String key) {
        Set<String> s = sets.get(key);
        return s != null ? s.size() : 0;
    }

    public boolean setContains(String key, String value) {
        Set<String> s = sets.get(key);
        return s != null && s.contains(value);
    }

    public double getDouble(String key) {
        Double val = doubles.get(key);
        return val != null ? val : 0.0;
    }

    public double addDouble(String key, double amount) {
        return doubles.merge(key, amount, Double::sum);
    }

    public void setDouble(String key, double value) {
        doubles.put(key, value);
    }

    public boolean getBooleanFlag(String key) {
        return getCounter(key) > 0;
    }

    public void setBooleanFlag(String key) {
        counters.put(key, 1L);
    }

    public ConcurrentHashMap<String, Long> getCountersMap()     { return counters; }
    public ConcurrentHashMap<String, Set<String>> getSetsMap()    { return sets; }
    public ConcurrentHashMap<String, Double> getDoublesMap()    { return doubles; }
}
