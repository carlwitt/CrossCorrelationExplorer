package Data;

import java.util.HashSet;
import java.util.Observable;
import java.util.Set;
import java.util.TreeMap;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

/**
 * Stores the data that is shared between the different views. 
 * @author Carl Witt
 */
public class SharedData extends Observable {

    public DataModel dataModel = new DataModel();
    
    public static class DataModel extends TreeMap<Integer, TimeSeries>{
        
        public final ObservableMap<Integer, TimeSeries> timeSeries = FXCollections.observableMap(this);
        
        public double getMinX(){
            double min = Double.POSITIVE_INFINITY;
            for (TimeSeries ts : values()) {
                for (int i = 0; i < ts.getDataItems().re.length; i++) {
                    min = Math.min(min, Double.isNaN(ts.getDataItems().im[i]) ? Double.POSITIVE_INFINITY : ts.getDataItems().re[i]);
                }
            }
            return min;
        }
        public double getMinY(){
            double min = Double.POSITIVE_INFINITY;
            for (TimeSeries ts : values()) {
                min = Math.min(min, ts.getDataItems().getMin(ComplexSequence.Part.IMAGINARY));
            }
            return min;
        }
        public double getMaxX(){
            double max = Double.NEGATIVE_INFINITY;
            for (TimeSeries ts : values()) {
                for (int i = 0; i < ts.getDataItems().re.length; i++) {
                    max = Math.max(max, Double.isNaN(ts.getDataItems().im[i]) ? Double.NEGATIVE_INFINITY : ts.getDataItems().re[i]);
                }
            }
            return max;
        }
        public double getMaxY(){
            double max = Double.NEGATIVE_INFINITY;
            for (TimeSeries ts : values()) {
                max = Math.max(max, ts.getDataItems().getMax(ComplexSequence.Part.IMAGINARY));
            }
            return max;
        }
    
    } 
    /** The subsets represent selections of time series (subsets of all loaded time series).
     * When computing cross correlations, each member of a subset A is cross correlated with each member of subset B. */
//    private final Set<DataModel.TimeSeriesSet> subsets = new HashSet<DataModel.TimeSeriesSet>();
    
}
