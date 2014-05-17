package Data.Correlation;

import Data.TimeSeries;
import com.google.common.base.Joiner;
import com.sun.istack.internal.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author Carl Witt
 */
public class CorrelationMetadata {

    public final int windowSize;
    public final int tauMin, tauMax;

    /** Place a base window each baseWindowOffset values of the time series.
     *  A value of 1 results in windows at 0, 1, 2, ...
     *  A value of k results in windows at 0, k, 2k, ...
     */
    public final int baseWindowOffset;

    /** The list of all time series for which the correlogram was computed. */
    public final List<TimeSeries> setA;
    public final List<TimeSeries> setB;
    
    final CrossCorrelation.NA_ACTION naAction;
    
    public CorrelationMetadata(@NotNull TimeSeries seriesA, @NotNull TimeSeries seriesB, int windowSize, int tauMin, int tauMax, CrossCorrelation.NA_ACTION naAction, int baseWindowOffset){
        this.tauMin = tauMin;
        this.tauMax = tauMax;
        setA = new ArrayList<>(1);
        setA.add(seriesA);
        setB = new ArrayList<>(1);
        setB.add(seriesB);
        this.naAction = naAction;
        this.windowSize = windowSize;
        this.baseWindowOffset = baseWindowOffset;
    }
    public CorrelationMetadata(@NotNull List<TimeSeries> setA, @NotNull List<TimeSeries> setB, int windowSize, int tauMin, int tauMax, CrossCorrelation.NA_ACTION naAction, int baseWindowOffset) {
        this.setA = setA;
        this.setB = setB;
        this.tauMin = tauMin;
        this.tauMax = tauMax;
        this.naAction = naAction;
        this.windowSize = windowSize;
        this.baseWindowOffset = baseWindowOffset;
    }

    public String toString() {
        return String.format("Metadata{windowSize: %d, baseWindowOffset: %d, tauMin: %d, tauMax: %s setA: %s setB: %s}",
                windowSize, baseWindowOffset, tauMin, tauMax,
                Joiner.on(",").join(setA),
                Joiner.on(",").join(setB));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CorrelationMetadata that = (CorrelationMetadata) o;

        if (baseWindowOffset != that.baseWindowOffset) return false;
        if (tauMax != that.tauMax) return false;
        if (tauMin != that.tauMin) return false;
        if (windowSize != that.windowSize) return false;
        if (naAction != that.naAction) return false;
        if (!setA.equals(that.setA)) return false;
        if (!setB.equals(that.setB)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = windowSize;
        result = 31 * result + tauMin;
        result = 31 * result + tauMax;
        result = 31 * result + baseWindowOffset;
        result = 31 * result + setA.hashCode();
        result = 31 * result + setB.hashCode();
        result = 31 * result + naAction.hashCode();
        return result;
    }

}
