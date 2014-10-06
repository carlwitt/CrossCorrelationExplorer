package Data.Windowing;

import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CrossCorrelation;
import Data.TimeSeries;
import com.google.common.base.Joiner;
import com.sun.istack.internal.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * This class describes how to compute something from a pair of time series by taking windows from them and then comparing the windows.<br/>
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
    public final int tauMin, tauMax, tauStep;

    /** Place a base window each baseWindowOffset values of the time series.
     *  A value of 1 results in windows at 0, 1, 2, ...
     *  A value of k results in windows at 0, k, 2k, ...
     */
    public final int baseWindowOffset;

    /** The number of base windows that completely fit in the time series (no shorter windows than |w|).
     * This equals the number of columns in the correlation matrix.  */
    public final int numBaseWindows;

    /** The number of lag windows that are needed both in the cross-correlation computation of a base window b and its subsequent base window b'.
      * This is the size of the intersection of the ranges [s_b + tauMin ... s_b + tauMax] and [s_b' + tauMin ... s_b' + tauMax]. */
    private final int lagRangeOverlap;

    /** The list of all time series for which the correlogram was computed. */
    public final List<TimeSeries> setA;
    public final List<TimeSeries> setB;

    public final CrossCorrelation.NA_ACTION naAction = CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED;

    public final HashMap<String, Object> customParameters = new HashMap<>();

    public WindowMetadata(@NotNull TimeSeries seriesA, @NotNull TimeSeries seriesB, int windowSize, int tauMin, int tauMax, int tauStep, int baseWindowOffset){
        this.tauMin = tauMin;
        this.tauMax = tauMax;
        this.tauStep= tauStep;
        setA = new ArrayList<>(1);
        setA.add(seriesA);
        setB = new ArrayList<>(1);
        setB.add(seriesB);
//        this.naAction = naAction;
        this.windowSize = windowSize;
        this.baseWindowOffset = baseWindowOffset;

        numBaseWindows = getNumberOfBaseWindows();
        lagRangeOverlap = getLagRangeOverlap();
    }

    public WindowMetadata(@NotNull List<TimeSeries> setA, @NotNull List<TimeSeries> setB, int windowSize, int tauMin, int tauMax, int tauStep, int baseWindowOffset) {
        this.setA = new ArrayList<>(setA);
        this.setB = new ArrayList<>(setB);
        this.tauMin = tauMin;
        this.tauMax = tauMax;
        this.tauStep= tauStep;
//        this.naAction = naAction;
        this.windowSize = windowSize;
        this.baseWindowOffset = baseWindowOffset;

        numBaseWindows = getNumberOfBaseWindows();
        lagRangeOverlap = getLagRangeOverlap();
    }

    private WindowMetadata(Builder builder){
        this.setA = new ArrayList<>(builder.setA);
        this.setB = new ArrayList<>(builder.setB);
        this.tauMin = builder.tauMin;
        this.tauMax = builder.tauMax;
        this.tauStep= builder.tauStep;
//        this.naAction = builder.naAction;
        this.windowSize = builder.windowSize;
        this.baseWindowOffset = builder.baseWindowOffset;
        CorrelationMatrix.setSignificanceLevel(this, builder.pValue);
        numBaseWindows = getNumberOfBaseWindows();
        lagRangeOverlap = getLagRangeOverlap();
    }

    /**
     * Helpful to compute the size of a column of the matrix.
     * Example: tau min = -10, tau max = 10, tau step = 1, result = 21 (-10, -9, ..., -1, 0, 1, 2, ..., 10)
     * Example: tau min = -10, tau max = 10, tau step = 4, result =  6 (-10, -6, -2, 2, 6, 10)
     * @return the number of integer time lags that are covered by the given tau min, tau max and tau step.
     */
    public int getNumberOfDifferentTimeLags(){
        return (int) Math.round(Math.ceil( 1.f * (tauMax-tauMin+1) / tauStep));
    }
    public int[] getDifferentTimeLags(){
        int[] lags = new int[getNumberOfDifferentTimeLags()];
        for (int i = 0; i < lags.length; i++) {
            lags[i] = tauMin + i*tauStep;
        }
        return lags;
    }


    /** Computes {@link #numBaseWindows}. */
    private int getNumberOfBaseWindows() {
        int timeSeriesLength = setA.get(0).getSize();
        assert baseWindowOffset > 0 : "Illegal base window offset. Must be larger than zero.";
        return (int) Math.ceil(1. * timeSeriesLength / baseWindowOffset);
//        used in the asymmetric computation
//        int largestValidWindowStartIndex = timeSeriesLength - windowSize; // N-windowSize+1 would be one-based
//        return largestValidWindowStartIndex / baseWindowOffset + 1;    // +1: the first window is located at index zero
    }

    int getLagRangeOverlap() {
        return tauMax - tauMin - baseWindowOffset + 1;
    }

    public double getMinXValue() {
        assert(setA.size() > 0) : "Metadata problem " + this;
        return setA.get(0).getDataItems().re[0];
    }

    public double getTimeInterval() {
        assert setA.size() > 0 : "At least one time series required in input set A.";
        assert setA.get(0).getDataItems().size() > 1 : "Time Series must contain at least to data points.";
        return setA.get(0).getDataItems().re[1] - setA.get(0).getDataItems().re[0];
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Builder constructor pattern
    // -----------------------------------------------------------------------------------------------------------------

    public static class Builder{

        public final int tauMin;
        public final int tauMax;
        public final int tauStep;
        public final int windowSize;
        public final int baseWindowOffset;
        public double pValue = 0.05;
        final List<TimeSeries> setA = new ArrayList<>();
        final List<TimeSeries> setB = new ArrayList<>();
        CrossCorrelation.NA_ACTION naAction = CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED;

        public Builder(int tauMin, int tauMax, int windowSize, int tauStep, int baseWindowOffset) {
            this.tauMin = tauMin;
            this.tauMax = tauMax;
            this.tauStep= tauStep;
            this.windowSize = windowSize;
            this.baseWindowOffset = baseWindowOffset;
        }
        public Builder tsA(TimeSeries ts){ setA.add(ts); return this; }
        public Builder tsA(Collection<TimeSeries> ts){ setA.addAll(ts); return this; }
        public Builder tsB(TimeSeries ts){ setB.add(ts); return this; }
        public Builder tsB(Collection<TimeSeries> ts){ setB.addAll(ts); return this; }
        public Builder pValue(double pValue) { this.pValue = pValue; return this; }
        //        public Builder naAction(CrossCorrelation.NA_ACTION naAction){ this.naAction = naAction; return this; }
        public WindowMetadata build(){return new WindowMetadata(this);}
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------------------------------------------------

    public String toString() {
        return String.format("Metadata{windowSize: %d, baseWindowOffset: %d, tauMin: %d, tauMax: %s tauStep: %s setA: %s setB: %s}",
                windowSize, baseWindowOffset, tauMin, tauMax, tauStep,
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
        if (tauStep != that.tauStep) return false;
        if (windowSize != that.windowSize) return false;
        if (naAction != that.naAction) return false;
        if (!setA.equals(that.setA)) return false;
        if (!setB.equals(that.setB)) return false;
        System.out.println(String.format("customParameters: %s", customParameters));
        System.out.println(String.format("that.customParameters: %s", that.customParameters));
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

    /** These methods are used to display metadata objects in the results table (via the cell value factory). */
    public Integer getInputSet1Size(){return setA.size(); }
    public Integer getInputSet2Size(){return setB.size(); }
    public Integer getWindowSize(){ return windowSize; }
    public Integer getOverlap(){ return windowSize-baseWindowOffset; }
    public Double getSignificanceLevel(){ return CorrelationMatrix.getSignificanceLevel(this); }
    public String getLagRange(){ return String.format("[%s, %s]",tauMin,tauMax); }
    public Integer getLagStep(){ return tauStep; }
}
