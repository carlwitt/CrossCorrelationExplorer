package Data.Correlation;

import Data.TimeSeries;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Carl Witt
 */
public class CorrelogramMetadata {

    public final int windowSize;
    /** The list of all time series for which the correlogram was computed. Invariant: the list must be sorted. */
    public List<TimeSeries> timeSeries;
    
    public CorrelogramMetadata(@NotNull TimeSeries timeSeriesA, @NotNull TimeSeries timeSeriesB, int windowSize) {
        this.windowSize = windowSize;
        ArrayList<TimeSeries> timeSeries = new ArrayList<TimeSeries>(2);
        timeSeries.add(timeSeriesA);
        timeSeries.add(timeSeriesB);
        this.setTimeSeries(timeSeries);
    }
    
    public CorrelogramMetadata(@NotNull List<TimeSeries> timeSeries, int windowSize) {
        this.windowSize = windowSize;
        this.timeSeries = timeSeries;
        this.setTimeSeries(timeSeries);
    }

    public String toString() {
        String result = "Metadata. Window size = "+windowSize+" for time series: ";
        for(TimeSeries t : timeSeries){
            result += t.id + ", ";
        }
        return result; //String.format("Metadata for time series: %s windowSize: %d", Joiner.on(",").join(timeSeries), windowSize);
    }

    private void setTimeSeries(List<TimeSeries> timeSeries) {
        Collections.sort(timeSeries);
        this.timeSeries = timeSeries;
    }
    
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + this.windowSize;
        hash = 79 * hash + (this.timeSeries != null ? this.timeSeries.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CorrelogramMetadata other = (CorrelogramMetadata) obj;
        if (this.windowSize != other.windowSize) {
            return false;
        }
        
        // compares the time series ids 
        // assumes that the list of time series is sorted by time series id.
        // this is a fast way to deal with permutations
        for (int i = 0; i < this.timeSeries.size(); i++) {
            if(this.timeSeries.get(i).id != other.timeSeries.get(i).id){
                return false;
            }
        }
        return true;
    }


}
