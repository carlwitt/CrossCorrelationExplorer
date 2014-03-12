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

//    public DataModel dataModel;
    public final ObservableMap<Integer, TimeSeries> timeSeries = FXCollections.observableMap(new TreeMap<Integer, TimeSeries>());
    
    /** The subsets represent selections of time series (subsets of all loaded time series).
     * When computing cross correlations, each member of a subset A is cross correlated with each member of subset B. */
//    private final Set<DataModel.TimeSeriesSet> subsets = new HashSet<DataModel.TimeSeriesSet>();
    
}
