package Data.Windowing;

import Data.Correlation.CrossCorrelation;
import Data.TimeSeries;
import com.google.common.base.Joiner;
import com.sun.istack.internal.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class describes how to compare time series by taking windows from them and then comparing the windows.<br/>
 * This is called metadata because it describes the input of a computation that gives a {@link Data.Statistics.StatisticsMatrix}.<br/><br/>
 *
 * Definitions:<br/>
 * <ul>
 *   <li>A window of a time series is a continuous subsequence of its values.</li> 
 *   <li>All windows have the same length |w|.</li>
 *   <li>The start index of the i-th window is denoted by s<sub>i</sub>.</li>
 *   <li>The precomputed windows are enumerated with an index from 1 to l.</li>
 *
 *   <li>A base window has s<sub>i</sub> = k*baseWindowOffset (k = 0,1,2,...).</li>
 *   <li>A lag window has s<sub>i</sub> = s<sub>b</sub> + tau (tau = tauMin, tauMin+1, ..., tauMax), where s<sub>b</sub> = k*baseWindowOffset.
 *      The range [s<sub>b</sub> + tauMin ... s<sub>b</sub> + tauMax] is called the lag range for base window b.</li>
 *   <li>A shared lag window is a window that belongs to more than one base window.
 *      Example: baseWindowOffset = 1. A window that starts at index 1 can be assigned the base window starting at index 0 (tau = 1),
 *      or the base window starting at index 1 (tau = 0) or at index 2 (tau = -1), etc.</li>
 * </ul>
 * @author Carl Witt
 */
public class WindowMetadata {

    public final int windowSize;
    public final int tauMin, tauMax;

    /** Place a base window each baseWindowOffset values of the time series.
     *  A value of 1 results in windows at 0, 1, 2, ...
     *  A value of k results in windows at 0, k, 2k, ...
     */
    public final int baseWindowOffset;

    /** the number of base windows that completely fit in the time series (no shorter windows than |w|) */
    public final int numBaseWindows;

    /** The number of lag windows that are needed both in the cross-correlation computation of a base window b and its subsequent base window b'.
      * This is the size of the intersection of the ranges [s_b + tauMin ... s_b + tauMax] and [s_b' + tauMin ... s_b' + tauMax]. */
    private final int lagRangeOverlap;

    /** The list of all time series for which the correlogram was computed. */
    public final List<TimeSeries> setA;
    public final List<TimeSeries> setB;

    public final CrossCorrelation.NA_ACTION naAction;

    public final HashMap<String, Object> customParameters = new HashMap<>();

    public WindowMetadata(@NotNull TimeSeries seriesA, @NotNull TimeSeries seriesB, int windowSize, int tauMin, int tauMax, CrossCorrelation.NA_ACTION naAction, int baseWindowOffset){
        this.tauMin = tauMin;
        this.tauMax = tauMax;
        setA = new ArrayList<>(1);
        setA.add(seriesA);
        setB = new ArrayList<>(1);
        setB.add(seriesB);
        this.naAction = naAction;
        this.windowSize = windowSize;
        this.baseWindowOffset = baseWindowOffset;

        numBaseWindows = getNumberOfBaseWindows();
        lagRangeOverlap = getLagRangeOverlap();
    }

    public WindowMetadata(@NotNull List<TimeSeries> setA, @NotNull List<TimeSeries> setB, int windowSize, int tauMin, int tauMax, CrossCorrelation.NA_ACTION naAction, int baseWindowOffset) {
        this.setA = setA;
        this.setB = setB;
        this.tauMin = tauMin;
        this.tauMax = tauMax;
        this.naAction = naAction;
        this.windowSize = windowSize;
        this.baseWindowOffset = baseWindowOffset;

        numBaseWindows = getNumberOfBaseWindows();
        lagRangeOverlap = getLagRangeOverlap();
    }


    private int getNumberOfBaseWindows() {
        int timeSeriesLength = setA.get(0).getSize();
        int largestValidWindowStartIndex = timeSeriesLength - windowSize; // N-windowSize+1 would be one-based

        // the number of base windows that completely fit in the time series (no shorter windows than |w|)
        return largestValidWindowStartIndex / baseWindowOffset + 1;    // +1: the first window is located at index zero
    }

    int getLagRangeOverlap() {
        return tauMax - tauMin - baseWindowOffset + 1;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------------------------------------------------

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

        WindowMetadata that = (WindowMetadata) o;

        if (baseWindowOffset != that.baseWindowOffset) return false;
        if (tauMax != that.tauMax) return false;
        if (tauMin != that.tauMin) return false;
        if (windowSize != that.windowSize) return false;
        if (naAction != that.naAction) return false;
        if (!setA.equals(that.setA)) return false;
        if (!setB.equals(that.setB)) return false;
        if (!customParameters.equals(that.customParameters)) return false;

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
        result = 31 * result + numBaseWindows;
        result = 31 * result + lagRangeOverlap;
        result = 31 * result + customParameters.hashCode();
        return result;
    }
}
