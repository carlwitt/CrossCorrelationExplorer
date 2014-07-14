package Data;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

import java.util.*;

/**
 * Stores time series and aggregated data on them.
 * Time series are organized in sets of files, because series in a single file refer to the same real world phenomenon.
 * Each file is associated with an ID. Currently, only two input files are supported.
 *
 * Note: The treemap class might not be the most efficient data structure, but it is convenient since it can be used to retrieve any time series from the set (e.g. using the firstEntry() function).
 * Since there are only very few operations on the data structure, this is perfectly o.k.
 *
 * @author Carl Witt
 */
public class DataModel {

    /** Stores the time series by their file id (input file A = 0 or input file B = 1) and their time series ID, which, nevertheless, should be unique. */
    private List<ObservableMap<Integer,TimeSeries>> timeSeriesByFile;

    /** each time series in input set A will be cross correlated with each time series in input set B. should be a set, actually but sequential access is useful for caching routines in cross correlation computations. */
    public final ObservableList<TimeSeries> correlationSetA = FXCollections.observableArrayList();
    public final ObservableList<TimeSeries> correlationSetB = FXCollections.observableArrayList();

//    public FXCollections.
    public ObservableList<TimeSeries> timeSeriesA = FXCollections.observableArrayList();
    public ObservableList<TimeSeries> timeSeriesB = FXCollections.observableArrayList();


    /** These cache the minimum and maximum Y value of the current dataset. Are invalidated (set to null) when time series are added or removed. */
    private Double minY = null;
    private Double maxY = null;

    public DataModel(Collection<Collection<TimeSeries>> tsByFile){

        timeSeriesByFile = new ArrayList<>();
        for (Collection<TimeSeries> tss : tsByFile){
            HashMap<Integer, TimeSeries> map = new HashMap<>();
            for(TimeSeries ts : tss) map.put(ts.getId(), ts);
            timeSeriesByFile.add(FXCollections.observableMap(map));
        }

        timeSeriesA = FXCollections.observableArrayList(timeSeriesByFile.get(0).values());
        timeSeriesB = FXCollections.observableArrayList(timeSeriesByFile.get(1).values());

    }
    public DataModel(){
        timeSeriesByFile = Arrays.asList(
                FXCollections.observableMap(new HashMap<>()),
                FXCollections.observableMap(new HashMap<>()));
//        timeSeries.addListener(new MapChangeListener<Integer, TimeSeries>() {
//            @Override public void onChanged(MapChangeListener.Change<? extends Integer, ? extends TimeSeries> change) {
//                if(change.wasRemoved()){
//                    loadedSeries.remove(change.getValueRemoved());
//                    minY = null;
//                    maxY = null;
//                }
//                if( change.wasAdded()){
////                    Logger.getAnonymousLogger().log(Level.INFO, String.format("Datamodel extended by time series %s: %s",change.getValueAdded().getId(), change.getValueAdded()));
//                    TimeSeries newTS = change.getValueAdded();
//                    loadedSeries.add(newTS);
//                    if(timeSeriesByFile.get(0).containsKey(newTS.getId()))
//                        timeSeriesA.add(newTS);
//                    else
//                        timeSeriesB.add(newTS);
//
//                    loadedSeries.sort(null);
//                    minY = null;
//                    maxY = null;
//                }
//            }
//        });
    }

    public int getTimeSeriesLength(int fileID) {
        if (timeSeriesByFile.get(fileID).isEmpty()) {
            return 0;
        }
        return timeSeriesByFile.get(fileID).values().iterator().next().getDataItems().re.length;
    }

    /** @return the smallest x value among all time series. It is assumed that it is the same for each time series.
     * @param fileID the time series group among which the smallest x value is found.
     */
    public double getMinX(int fileID) {

        TimeSeries anyTimeSeries = timeSeriesByFile.get(fileID).values().iterator().next();
//        TimeSeries anyTimeSeries = allTimeSeries.column(fileID).values().iterator().next();
        if(anyTimeSeries.getSize() == 0) return 0;
        return anyTimeSeries.getDataItems().re[0];

        // the use of this method was to ignore NaN Y-values when determining the minimum X value.
//            for (int i = 0; i < ts.getDataItems().re.length; i++) {
//                min = Math.min(min, Double.isNaN(ts.getDataItems().im[i]) ? Double.POSITIVE_INFINITY : ts.getDataItems().re[i]);
//            }
    }

    /** @return the largest x value among all time series. It is assumed that it is the same for each time series.
     * @param fileID the time series group among which the largest x value is found.
     */
    public double getMaxX(int fileID) {

        TimeSeries anyTimeSeries = timeSeriesByFile.get(fileID).values().iterator().next();
//        TimeSeries anyTimeSeries = allTimeSeries.column(fileID).values().iterator().next();
        if(anyTimeSeries.getSize() == 0) return 0;
        return anyTimeSeries.getDataItems().re[anyTimeSeries.getDataItems().re.length-1];

        // the use of this method was to ignore NaN Y-values when determining the minimum X value.
//        for (TimeSeries ts : values()) {
//            for (int i = 0; i < ts.getDataItems().re.length; i++) {
//                max = Math.max(max, Double.isNaN(ts.getDataItems().im[i]) ? Double.NEGATIVE_INFINITY : ts.getDataItems().re[i]);
//            }
//        }
    }

    public double getMinY(int fileID) {
        // compute if not cached
        if(minY == null){
            minY = Double.POSITIVE_INFINITY;
            for (TimeSeries ts : timeSeriesByFile.get(fileID).values()) {
//            for (TimeSeries ts : allTimeSeries.column(fileID).values()) {
                minY = Math.min(minY, ts.getDataItems().getMin(ComplexSequence.Part.IMAGINARY));
            }

        }
        return minY;
    }

    public double getMaxY(int fileID) {
        // compute if not cached
        if(maxY == null){
            maxY = Double.NEGATIVE_INFINITY;
            for (TimeSeries ts : timeSeriesByFile.get(fileID).values()) {
//            for (TimeSeries ts : allTimeSeries.column(fileID).values()) {
                maxY = Math.max(maxY, ts.getDataItems().getMax(ComplexSequence.Part.IMAGINARY));
            }

        }

        return maxY;
    }

    /** @return the number of data points in the specified interval (e.g. time period) */
    public double getNumDataPointsInRange(double lowerBound, double upperBound) {
        return Math.ceil(upperBound-lowerBound);
    }

    public void put(int fileID, int timeSeriesID, TimeSeries timeSeries) {
        timeSeriesByFile.get(fileID).put(timeSeriesID, timeSeries);
        if(fileID == 0)timeSeriesA.add(timeSeries);
        if(fileID == 1)timeSeriesB.add(timeSeries);
    }

    public TimeSeries get(int fileIdx, int i) {
        TimeSeries timeSeries = timeSeriesByFile.get(fileIdx).get(i);
        if(timeSeries != null) return timeSeries;
        assert false : String.format("Time series with the given ID %s doesn't exist in the data model.", i);
        return null;
    }

    public Map<Integer, TimeSeries> getFileSeries(int fileID) {
        return timeSeriesByFile.get(fileID);
    }

    public void clear() {
        for (ObservableMap<Integer,TimeSeries> m :timeSeriesByFile){
            m.clear();
        }
    }

    public int getNumFiles() {
        return timeSeriesByFile.size();
    }

    public Iterator<TimeSeries> getTimeSeriesIterator() {
        return new Iterator<TimeSeries>() {
            int fileIdx = 0;
            Iterator<TimeSeries> currentIterator = timeSeriesByFile.get(fileIdx).values().iterator();
            @Override public boolean hasNext() {
                ObservableMap<Integer, TimeSeries> nextSet = timeSeriesByFile.get(fileIdx + 1);
                return currentIterator.hasNext() || (nextSet != null && nextSet.size() > 0);
            }

            @Override public TimeSeries next() {
                if(!currentIterator.hasNext()){
                    fileIdx++;
                    currentIterator = timeSeriesByFile.get(fileIdx).values().iterator();
                }
                return currentIterator.next();
            }
        };
    }

    public int getNumberOfTimeSeries() {
        int count = 0;
        for (Map<Integer, TimeSeries> map : timeSeriesByFile) count += map.size();
        return count;
    }
    public int getNumberOfTimeSeries(int fileIdx) {
        return timeSeriesByFile.get(fileIdx).size();
    }
}