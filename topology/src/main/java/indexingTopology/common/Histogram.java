package indexingTopology.common;

import java.io.Serializable;
import java.util.*;

/**
 * Created by acelzj on 12/12/16.
 */
public class Histogram implements Serializable{

    private Map<Integer, Long> histogram;

    private int numberOfIntervals;

    public Histogram(int numberOfIntervals) {
        this.numberOfIntervals = numberOfIntervals;
        histogram = new HashMap<>();
    }

    public Histogram(Map<Integer, Long> histogram, int numberOfIntervals) {
        this.numberOfIntervals = numberOfIntervals;
        this.histogram = new HashMap<>();
        this.histogram.putAll(histogram);
    }

    public void record(int intervalId) {
        Long frequency = histogram.computeIfAbsent(intervalId, t -> 0L);
        histogram.put(intervalId, frequency + 1L);
    }

    public Map<Integer, Long> getHistogram() {
        return histogram;
    }

    public List<Long> histogramToList() {
        List<Long> ret = new ArrayList<>();
        setDefaultValueForAbsentKey(numberOfIntervals);
        Object[] keys = histogram.keySet().toArray();
        Arrays.sort(keys);
        for (Object key : keys) {
            ret.add(histogram.get(key));
        }
        return ret;
    }

    public void setDefaultValueForAbsentKey(int numberOfKeys) {
        for(int i = 0; i< numberOfKeys; i++ ) {
            if(!histogram.containsKey(i)) {
                histogram.put(i, 0L);
            }
        }
    }

    public void merge(Histogram his) {
        for (Integer key : his.getHistogram().keySet()) {
            if (histogram.containsKey(key)) {
                histogram.put(key, histogram.get(key) + his.getHistogram().get(key));
            } else {
                histogram.put(key, his.getHistogram().get(key));
            }
        }
    }

    public void clear() {
        histogram.clear();
    }

    public String toString() {
        TreeMap<Integer, Long> treeMap = new TreeMap<>();
        treeMap.putAll(histogram);
        String str = "";
        for(Integer i: treeMap.keySet()) {
            if (treeMap.get(i) != 0)
                str += String.format("%d: %d, ", i, treeMap.get(i));
        }
        return str.substring(0, Math.max(str.length() - 2, 0));
    }

}
