package Data;

import java.util.Arrays;
import java.util.TreeMap;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

/**
 * Stores time series and aggregated data on them.
 * @author Carl Witt
 */
public class DataModel extends TreeMap<Integer, TimeSeries> {
    
    /** Contains the mappings between integer IDs (1-based) and the time series object references */
    public final ObservableMap<Integer, TimeSeries> timeSeries = FXCollections.observableMap(this);
    /** This observable list contains the indices of all currently loaded time series. It can be used to monitor the currently loaded time series via a simply integer list view display (no conversion between time series and their indices necessary). */
    private ObservableList<TimeSeries> loadedSeries = FXCollections.observableArrayList();
    
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

    public double getMinX() {
        double min = Double.POSITIVE_INFINITY;
        for (TimeSeries ts : values()) {
            for (int i = 0; i < ts.getDataItems().re.length; i++) {
                min = Math.min(min, Double.isNaN(ts.getDataItems().im[i]) ? Double.POSITIVE_INFINITY : ts.getDataItems().re[i]);
            }
        }
        return min;
    }

    public double getMinY() {
        double min = Double.POSITIVE_INFINITY;
        for (TimeSeries ts : values()) {
            min = Math.min(min, ts.getDataItems().getMin(ComplexSequence.Part.IMAGINARY));
        }
        return min;
    }

    public double getMaxX() {
        double max = Double.NEGATIVE_INFINITY;
        for (TimeSeries ts : values()) {
            for (int i = 0; i < ts.getDataItems().re.length; i++) {
                max = Math.max(max, Double.isNaN(ts.getDataItems().im[i]) ? Double.NEGATIVE_INFINITY : ts.getDataItems().re[i]);
            }
        }
        return max;
    }

    public double getMaxY() {
        double max = Double.NEGATIVE_INFINITY;
        for (TimeSeries ts : values()) {
            max = Math.max(max, ts.getDataItems().getMax(ComplexSequence.Part.IMAGINARY));
        }
        return max;
    }

    public ObservableList<TimeSeries> getObservableLoadedSeries() {
        return loadedSeries;
    }

    /** @return the number of data points in the specified interval (e.g. time period) */
    public double getNumDataPointsInRange(double lowerBound, double upperBound) {
        return Math.ceil(upperBound-lowerBound);
    }

}