package Data;

import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

import java.util.TreeMap;

/**
 * Stores time series and aggregated data on them.
 * @author Carl Witt
 */
public class DataModel extends TreeMap<Integer, TimeSeries> {
    
    /** Contains the mappings between integer IDs (1-based) and the time series object references */
    public final ObservableMap<Integer, TimeSeries> timeSeries = FXCollections.observableMap(this);
    /** This observable list contains the indices of all currently loaded time series. It can be used to monitor the currently loaded time series via a simply integer list view display (no conversion between time series and their indices necessary). */
    private final ObservableList<TimeSeries> loadedSeries = FXCollections.observableArrayList();
    
    public DataModel(){
        timeSeries.addListener(new MapChangeListener<Integer, TimeSeries>() {
            @Override public void onChanged(MapChangeListener.Change<? extends Integer, ? extends TimeSeries> change) {
                if(change.wasRemoved()){
                    loadedSeries.remove(change.getValueRemoved());
                } 
                if( change.wasAdded()){
                    loadedSeries.add(change.getValueAdded());
                    loadedSeries.sort(null);
                }
            }
        });
    }

    public int getTimeSeriesLength() {
        if (isEmpty()) {
            return 0;
        }
        return firstEntry().getValue().getDataItems().re.length;
    }

    /** @return the smallest x value among all time series. It is assumed that it is the same for each time series. */
    public double getMinX() {

        TimeSeries anyTimeSeries = firstEntry().getValue();
        if(anyTimeSeries.getSize() == 0) return 0;
        return anyTimeSeries.getDataItems().re[0];

        // the use of this method was to ignore NaN Y-values when determining the minimum X value.
//            for (int i = 0; i < ts.getDataItems().re.length; i++) {
//                min = Math.min(min, Double.isNaN(ts.getDataItems().im[i]) ? Double.POSITIVE_INFINITY : ts.getDataItems().re[i]);
//            }
    }

    /** @return the largest x value among all time series. It is assumed that it is the same for each time series. */
    public double getMaxX() {

        TimeSeries anyTimeSeries = firstEntry().getValue();
        if(anyTimeSeries.getSize() == 0) return 0;
        return anyTimeSeries.getDataItems().re[anyTimeSeries.getDataItems().re.length-1];

        // the use of this method was to ignore NaN Y-values when determining the minimum X value.
//        for (TimeSeries ts : values()) {
//            for (int i = 0; i < ts.getDataItems().re.length; i++) {
//                max = Math.max(max, Double.isNaN(ts.getDataItems().im[i]) ? Double.NEGATIVE_INFINITY : ts.getDataItems().re[i]);
//            }
//        }
    }

    /** These cache the minimum and maximum Y value of the current dataset. Are invalidated (set to null) when time series are added or removed. */
    Double minY = null, maxY = null;
    public double getMinY() {
        // compute if not cached
        if(minY == null){
            minY = Double.POSITIVE_INFINITY;
            for (TimeSeries ts : values()) {
                minY = Math.min(minY, ts.getDataItems().getMin(ComplexSequence.Part.IMAGINARY));
            }

        }
        return minY;
    }

    public double getMaxY() {
        // compute if not cached
        if(maxY == null){
            maxY = Double.NEGATIVE_INFINITY;
            for (TimeSeries ts : values()) {
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

}