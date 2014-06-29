package Data;

import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stores time series and aggregated data on them.
 * Time series are organized in sets of files, because series in a single file refer to the same real world phenomenon.
 * Each file is associated with an ID. Currently, only two input files are supported.
 *
 * Note: The treemap class might not be the most efficient data structure, but it is convenient since it can be used to retrieve any time series from the set (e.g. using the firstEntry() function).
 * Since there are only very few operations on the data structure, this is perfectly o.k.
 *
 * TODO: the {@link #allTimeSeries} data structures is a materialized (set theoretic) union of the time series sets stored in {@link #timeSeriesByFile}. providing an iterator might be a more elegant solution.
 * @author Carl Witt
 */
public class DataModel {

    /** Stores the time series by their file id (input file A = 0 or input file B = 1) and their time series ID, which, nevertheless, should be unique. */
    private TreeMap<Integer, TimeSeries> allTimeSeries = new TreeMap<>();
    private List<TreeMap<Integer,TimeSeries>> timeSeriesByFile = Arrays.asList(new TreeMap<>(), new TreeMap<>());

    /** each time series in input set A will be cross correlated with each time series in input set B */
    public final ObservableList<TimeSeries> correlationSetA = FXCollections.observableArrayList();
    public final ObservableList<TimeSeries> correlationSetB = FXCollections.observableArrayList();

    /** Contains the mappings between integer IDs (1-based) and the time series object references */
    public final ObservableMap<Integer, TimeSeries> timeSeries = FXCollections.observableMap(allTimeSeries);
    /** This observable list contains the indices of all currently loaded time series. It can be used to monitor the currently loaded time series via a simply integer list view display (no conversion between time series and their indices necessary). */
    private final ObservableList<TimeSeries> loadedSeries = FXCollections.observableArrayList();

    /** These cache the minimum and maximum Y value of the current dataset. Are invalidated (set to null) when time series are added or removed. */
    private Double minY = null;
    private Double maxY = null;

    public DataModel(){
        timeSeries.addListener(new MapChangeListener<Integer, TimeSeries>() {
            @Override public void onChanged(MapChangeListener.Change<? extends Integer, ? extends TimeSeries> change) {
                if(change.wasRemoved()){
                    loadedSeries.remove(change.getValueRemoved());
                    minY = null;
                    maxY = null;
                }
                if( change.wasAdded()){
//                    Logger.getAnonymousLogger().log(Level.INFO, String.format("Datamodel extended by time series %s: %s",change.getValueAdded().getId(), change.getValueAdded()));
                    loadedSeries.add(change.getValueAdded());
                    loadedSeries.sort(null);
                    minY = null;
                    maxY = null;
                }
            }
        });
    }

    public int getTimeSeriesLength(int fileID) {
        if (allTimeSeries.isEmpty()) {
            return 0;
        }
        return timeSeriesByFile.get(fileID).firstEntry().getValue().getDataItems().re.length;
//        return allTimeSeries.column(fileID).values().iterator().next().getDataItems().re.length;
    }

    /** @return the smallest x value among all time series. It is assumed that it is the same for each time series.
     * @param fileID*/
    public double getMinX(int fileID) {

        TimeSeries anyTimeSeries = timeSeriesByFile.get(fileID).firstEntry().getValue();
//        TimeSeries anyTimeSeries = allTimeSeries.column(fileID).values().iterator().next();
        if(anyTimeSeries.getSize() == 0) return 0;
        return anyTimeSeries.getDataItems().re[0];

        // the use of this method was to ignore NaN Y-values when determining the minimum X value.
//            for (int i = 0; i < ts.getDataItems().re.length; i++) {
//                min = Math.min(min, Double.isNaN(ts.getDataItems().im[i]) ? Double.POSITIVE_INFINITY : ts.getDataItems().re[i]);
//            }
    }

    /** @return the largest x value among all time series. It is assumed that it is the same for each time series.
     * @param fileID*/
    public double getMaxX(int fileID) {

        TimeSeries anyTimeSeries = timeSeriesByFile.get(fileID).firstEntry().getValue();
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

    public ObservableList<TimeSeries> getObservableLoadedSeries() {
        return loadedSeries;
    }

    /** @return the number of data points in the specified interval (e.g. time period) */
    public double getNumDataPointsInRange(double lowerBound, double upperBound) {
        return Math.ceil(upperBound-lowerBound);
    }

    /** Returns the length of all time series. Is assummed to be equal for all time series.
     * @param sharedData*/
    public int getTimeSeriesLength(SharedData sharedData){
        return correlationSetA.get(0).getDataItems().length;
    }

    public void put(int fileID, int timeSeriesID, TimeSeries timeSeries) {
        timeSeriesByFile.get(fileID).put(timeSeriesID, timeSeries);
        allTimeSeries.put(timeSeriesID,timeSeries);
//        allTimeSeries.put(fileID, timeSeriesID, timeSeries);
    }

    public TimeSeries get(int i) {
        return allTimeSeries.get(i);
//        return allTimeSeries.column(1).get(i);
    }

    public Map<Integer, TimeSeries> getFileSeries(int fileID) {
        return timeSeriesByFile.get(fileID);
    }

    public void clear() {
        timeSeriesByFile.get(0).clear();
        timeSeriesByFile.get(1).clear();
        allTimeSeries.clear();
    }

    public int getNumFiles() {
        return timeSeriesByFile.size();
    }
}