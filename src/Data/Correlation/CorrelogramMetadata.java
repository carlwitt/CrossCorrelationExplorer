package Data.Correlation;

import Data.TimeSeries;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author Carl Witt
 */
public class CorrelogramMetadata {

    public final int windowSize;
    /** The list of all time series for which the correlogram was computed. Invariant: the list must be sorted. */
    public List<TimeSeries> setA, setB;
    
    DFT.NA_ACTION naAction;
    
    public CorrelogramMetadata(@NotNull TimeSeries seriesA, @NotNull TimeSeries seriesB, int windowSize, DFT.NA_ACTION naAction){
        setA = new ArrayList<>(1);
        setA.add(seriesA);
        setB = new ArrayList<>(1);
        setB.add(seriesB);
        this.naAction = naAction;
        this.windowSize = windowSize;
    }
    public CorrelogramMetadata(@NotNull List<TimeSeries> setA, @NotNull List<TimeSeries> setB, int windowSize, DFT.NA_ACTION naAction) {
        this.setA = setA;
        this.setB = setB;
        this.naAction = naAction;
        this.windowSize = windowSize;
    }

    public String toString() {
        return String.format("Metadata{windowSize: %d setA: %s setB: %s}", windowSize, Joiner.on(",").join(setA), Joiner.on(",").join(setB));
    }
    
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 17 * hash + this.windowSize;
        hash = 17 * hash + Objects.hashCode(this.setA);
        hash = 17 * hash + Objects.hashCode(this.setB);
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
        if (!Objects.equals(this.setA, other.setA)) {
            return false;
        }
        if (!Objects.equals(this.setB, other.setB)) {
            return false;
        }
        return true;
    }

}
