package Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Observable;
import java.util.Set;
import java.util.TreeMap;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

/**
 * The data model keeps track of the currently instantiated time series and makes accessing them easier.
 * It also supports grouping time series into subsets which can than be used to cross correlate only some time series against each other.
 */
public class DataModel {
    
    /** Keeps track of the references to the loaded time series by their id. */
//    public final TreeMap<Integer, TimeSeries> index = new TreeMap<Integer, TimeSeries>();

    private final ObservableMap<Integer, TimeSeries> index = FXCollections.observableHashMap();
    
    /** @return the number of available time series **/
    public int getSize() {
        return index.keySet().size();
    }

    /** 
     * @param id identifier of the time series
     * @return true if the time series exists, false otherwise
     */
    public boolean contains(int id) {
        return index.containsKey(id);
    }
    /** 
     * @param id The time series id see id attribute in {@link TimeSeries}
     * @return Time series
     */
    public TimeSeries get(int id) {
        return index.get(id);
    }
    /** @return all time series registered in the data model */
    public Collection<TimeSeries> getAll() {
        return index.values();
    }

     /** @return The data model for fluid code writing */
    public DataModel add(TimeSeries ts) {
        index.put(ts.id, ts);
        return this;
    }
    /** @return The data model for fluid code writing */
    public DataModel remove(TimeSeries ts) {
        index.remove(ts.id);
        return this;
    }
    
//    /** A named set of time series. */
//    private int nextTimeSeriesSetId = 1;
//    public class TimeSeriesSet{
//
//        public final int id;
//        public String name;
//        Set<TimeSeries> set = new HashSet<TimeSeries>();
//
//        public TimeSeriesSet(String name){
//            this.id = nextTimeSeriesSetId;
//            nextTimeSeriesSetId++;
//            
//            this.name = name;
//        }
//        
//    }
    
    public ObservableMap<Integer, TimeSeries> getIndex(){ return index; }
    
}
