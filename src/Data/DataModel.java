package Data;

import Global.Util;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;

import java.util.*;

/**
 * Stores time series and aggregated data on them.
 * Time series are organized in ensembles, requiring that the time series in a file form an ensemble.
 * Each ensemble is associated with an ID. Currently, only two ensembles are supported, with the IDs 0 and 1.
 *
 * @author Carl Witt
 */
public class DataModel {

    /** Stores the time series by their file id (input file A = 0 or input file B = 1) and their time series ID, which, nevertheless, should be unique. */
    private List<ObservableMap<Integer,TimeSeries>> timeSeriesByEnsemble;
    public ObservableList<TimeSeries> ensemble1TimeSeries = FXCollections.observableArrayList();    // TODO: check if these are really necessary anymore (any observers?)
    public ObservableList<TimeSeries> ensemble2TimeSeries = FXCollections.observableArrayList();

    /** each time series in input set A will be cross correlated with each time series in input set B. should be a set, actually but sequential access is useful for caching routines in cross correlation computations. */
    public final ObservableList<TimeSeries> correlationSetA = FXCollections.observableArrayList();
    public final ObservableList<TimeSeries> correlationSetB = FXCollections.observableArrayList();

    public final TimeSeriesAverager correlationSetAAggregator = new TimeSeriesAverager(correlationSetA);
    public final TimeSeriesAverager correlationSetBAggregator = new TimeSeriesAverager(correlationSetB);

    /** These cache the minimum and maximum Y value of the current dataset. Are invalidated (set to null) when time series are added or removed.
     * The array index refers to the ensemble ID. */
    private Double[] minY = new Double[2];
    private Double[] maxY = new Double[2];

    public DataModel(Collection<Collection<TimeSeries>> tsByEnsemble){

        timeSeriesByEnsemble = new ArrayList<>();
        for (Collection<TimeSeries> tss : tsByEnsemble){
            HashMap<Integer, TimeSeries> map = new HashMap<>();
            for(TimeSeries ts : tss) map.put(ts.getId(), ts);
            timeSeriesByEnsemble.add(FXCollections.observableMap(map));
        }

        ensemble1TimeSeries = FXCollections.observableArrayList(timeSeriesByEnsemble.get(0).values());
        ensemble2TimeSeries = FXCollections.observableArrayList(timeSeriesByEnsemble.get(1).values());

    }
    public DataModel(){
        timeSeriesByEnsemble = Arrays.asList(
                FXCollections.observableMap(new HashMap<>()),
                FXCollections.observableMap(new HashMap<>()));
    }

    public int getTimeSeriesLength(int ensembleID) {
        if (timeSeriesByEnsemble.get(ensembleID).isEmpty()) {
            return 0;
        }
        return timeSeriesByEnsemble.get(ensembleID).values().iterator().next().getDataItems().re.length;
    }

    /** @return the smallest x value among all time series. It is assumed that it is the same for each time series.
     * @param ensembleID the time series group among which the smallest x value is found.
     */
    public double getMinX(int ensembleID) {

        assert timeSeriesByEnsemble.get(ensembleID).values().size() > 0 : "Ensemble " + ensembleID + " has no time series.";
        TimeSeries anyTimeSeries = timeSeriesByEnsemble.get(ensembleID).values().iterator().next();
//        TimeSeries anyTimeSeries = allTimeSeries.column(ensembleID).values().iterator().next();
        if(anyTimeSeries.getSize() == 0) return 0;
        return anyTimeSeries.getDataItems().re[0];

        // the use of this method was to ignore NaN Y-values when determining the minimum X value.
//            for (int i = 0; i < ts.getDataItems().re.length; i++) {
//                min = Math.min(min, Double.isNaN(ts.getDataItems().im[i]) ? Double.POSITIVE_INFINITY : ts.getDataItems().re[i]);
//            }
    }

    /** @return the largest x value among all time series. It is assumed that it is the same for each time series.
     * @param ensembleID the time series group among which the largest x value is found.
     */
    public double getMaxX(int ensembleID) {

        TimeSeries anyTimeSeries = timeSeriesByEnsemble.get(ensembleID).values().iterator().next();
//        TimeSeries anyTimeSeries = allTimeSeries.column(ensembleID).values().iterator().next();
        if(anyTimeSeries.getSize() == 0) return 0;
        return anyTimeSeries.getDataItems().re[anyTimeSeries.getDataItems().re.length-1];

        // the use of this method was to ignore NaN Y-values when determining the minimum X value.
//        for (TimeSeries ts : values()) {
//            for (int i = 0; i < ts.getDataItems().re.length; i++) {
//                max = Math.max(max, Double.isNaN(ts.getDataItems().im[i]) ? Double.NEGATIVE_INFINITY : ts.getDataItems().re[i]);
//            }
//        }
    }

    public double getMinY(int ensembleID) {
        // compute if not cached
        if(minY[ensembleID] == null){
            minY[ensembleID] = Double.POSITIVE_INFINITY;
            for (TimeSeries ts : timeSeriesByEnsemble.get(ensembleID).values()) {
//            for (TimeSeries ts : allTimeSeries.column(ensembleID).values()) {
                minY[ensembleID] = Math.min(minY[ensembleID], ts.getDataItems().getMin(ComplexSequence.Part.IMAGINARY));
            }

        }
        return minY[ensembleID];
    }

    public double getMaxY(int ensembleID) {
        // compute if not cached
        if(maxY[ensembleID] == null){
            maxY[ensembleID] = Double.NEGATIVE_INFINITY;
            for (TimeSeries ts : timeSeriesByEnsemble.get(ensembleID).values()) {
//            for (TimeSeries ts : allTimeSeries.column(ensembleID).values()) {
                maxY[ensembleID] = Math.max(maxY[ensembleID], ts.getDataItems().getMax(ComplexSequence.Part.IMAGINARY));
            }

        }

        return maxY[ensembleID];
    }

    /** @return the number of data points in the specified interval (e.g. time period) */
    public int getNumDataPointsInRange(int ensembleID, double lowerBound, double upperBound) {

        TimeSeries anyTimeSeries = ensemble1TimeSeries.get(ensembleID);
        double spacing = anyTimeSeries.getDataItems().re[1]-anyTimeSeries.getDataItems().re[0];
        return (int) Math.ceil((upperBound-lowerBound)/spacing);
    }

    public void put(int ensembleID, int timeSeriesID, TimeSeries timeSeries) {
        timeSeriesByEnsemble.get(ensembleID).put(timeSeriesID, timeSeries);
        if(ensembleID == 0) ensemble1TimeSeries.add(timeSeries);
        if(ensembleID == 1) ensemble2TimeSeries.add(timeSeries);
    }

    public TimeSeries get(int ensembleID, int i) {
        TimeSeries timeSeries = timeSeriesByEnsemble.get(ensembleID).get(i);
        if(timeSeries != null) return timeSeries;
        assert false : String.format("Time series with the given ID %s doesn't exist in the data model.", i);
        return null;
    }

    public Map<Integer, TimeSeries> getEnsemble(int ensembleID) {
        return timeSeriesByEnsemble.get(ensembleID);
    }

    public void clear() {
        for (ObservableMap<Integer,TimeSeries> m : timeSeriesByEnsemble){
            m.clear();
        }
    }

    public int getNumEnsembles() {
        return timeSeriesByEnsemble.size();
    }

    public Iterator<TimeSeries> getTimeSeriesIterator() {
        return new Iterator<TimeSeries>() {
            int ensembleID = 0;
            Iterator<TimeSeries> currentIterator = timeSeriesByEnsemble.get(ensembleID).values().iterator();
            @Override public boolean hasNext() {
                ObservableMap<Integer, TimeSeries> nextSet = timeSeriesByEnsemble.get(ensembleID + 1);
                return currentIterator.hasNext() || (nextSet != null && nextSet.size() > 0);
            }

            @Override public TimeSeries next() {
                if(!currentIterator.hasNext()){
                    ensembleID++;
                    currentIterator = timeSeriesByEnsemble.get(ensembleID).values().iterator();
                }
                return currentIterator.next();
            }
        };
    }

    public int getNumberOfTimeSeries() {
        int count = 0;
        for (Map<Integer, TimeSeries> map : timeSeriesByEnsemble) count += map.size();
        return count;
    }
    public int getNumberOfTimeSeries(int ensembleID) {
        return timeSeriesByEnsemble.get(ensembleID).size();
    }

    public int getNumberOfEnsembles() { return timeSeriesByEnsemble.size(); }

    /**
     * @return null if no time series are present. the minimum and maximum x and y values of all time series in all ensembles otherwise.
     */
    public static Bounds getDataBounds(List<TimeSeries> ensemble1, List<TimeSeries> ensemble2) {

        assert !(ensemble1.isEmpty() && ensemble2.isEmpty()) : "The data bounds of two empty ensembles are undefined.";

        if(ensemble1.isEmpty()) return getDataBounds(ensemble2);
        if(ensemble2.isEmpty()) return getDataBounds(ensemble1);

        return Util.union(getDataBounds(ensemble1), getDataBounds(ensemble2));

    }

    /**
     * @return null if no time series are present. the minimum and maximum x and y values of all time series in all ensembles otherwise.
     */
    public static Bounds getDataBounds(List<TimeSeries> ensemble) {

        if(ensemble.size() == 0) return null;

        double minX = ensemble.stream().map(TimeSeries::getMinX).reduce(Math::min).get();
        double maxX = ensemble.stream().map(TimeSeries::getMaxX).reduce(Math::max).get();
        double minY = ensemble.stream().map(TimeSeries::getMinY).reduce(Math::min).get();
        double maxY = ensemble.stream().map(TimeSeries::getMaxY).reduce(Math::max).get();

        return new BoundingBox(minX, minY, maxX-minX, maxY-minY);
    }
}