package Data.Correlation;

import Data.TimeSeries;

/**
 * Using implementations of this class, reusable parts of the cross-correlation computation can be precomputed at the expense of memory.
 * There's the {@link Data.Correlation.BaseWindowStatistics} class for heavy precompution of data and the {@link Data.Correlation.LagWindowStatistics} class
 * for lightweight precomputation of data and on-the-fly caching of reusable values.
 *
 * Definitions:
 *   - A window of a time series is a continuous subsequence of time series.
 *      All windows have the same length |w|.
 *      The start index of the i-th window is denoted by s_i.
 *      The precomputed windows are enumerated with an index from 1 to l.
 *
 *   - A base window has s_i = k*baseWindowOffset (k = 0,1,2,...).
 *   - A lag window has s_i = s_b + tau (tau = tauMin, tauMin+1, ..., tauMax), where s_b = k*baseWindowOffset.
 *      The range [s_b + tauMin ... s_b + tauMax] is called the lag range for base window b.
 *   - A shared lag window is a window that belongs to more than one base window.
 *      Example: baseWindowOffset = 1. A window that starts at index 1 can be assigned the base window starting at index 0 (tau = 1),
 *      or the base window starting at index 1 (tau = 0) or at index 2 (tau = -1), etc.
 *
 * Created by Carl Witt on 14.05.14.
 *
 */
public abstract class AbstractWindowStatistics {

    // -----------------------------------------------------------------------------------------------------------------
    // cross-correlation specific parameters
    // -----------------------------------------------------------------------------------------------------------------

    /** The time series for which to precompute the data. */
    final TimeSeries timeSeries;

    /** Size of the windows that are taken from a time series (denoted |w|). */
    final int windowSize;

    /** The offset between base windows. baseWindowOffset = s_b' - s_b where b is any base window and b' its subsequent base window. */
    final int baseWindowOffset;

    // -----------------------------------------------------------------------------------------------------------------
    // derived from specific parameters
    // -----------------------------------------------------------------------------------------------------------------

    /** Total number of windows to precompute, assuming only complete windows are considered. */
    int numWindows;

    /** The mean of window i. */
    public double[] means;

    /** The sum of the squared normalized values in window i. (x_i - average(window i))^2 */
    public double[] summedSquares;

    /** The mean-shifted values of window i (x - average of window i).
     * Note that the shared values between windows can not be reused since the means of the windows vary. */
    double[][] normalizedValues;

    /** The i-th array entry contains the start index (zero based) in the time series for the (i+1)-th window (because windows are enumerated 1-based) to precompute. */
    int[] windowStartIndices;

    // -----------------------------------------------------------------------------------------------------------------
    // methods
    // -----------------------------------------------------------------------------------------------------------------

    AbstractWindowStatistics(TimeSeries ts, int windowSize, int baseWindowOffset) {
        if(baseWindowOffset == 0) throw new AssertionError("Base window offset (baseWindowOffset) must be larger than zero.");
        this.timeSeries = ts;
        this.windowSize = windowSize;
        this.baseWindowOffset = baseWindowOffset;
    }

    /**
     * @param windowStartIndex the zero based index of the time series value where the windows starts.
     * @return the zero based window index
     */
    protected abstract int getWindowNumberForStartIndex(int windowStartIndex);

    protected abstract void computeWindowStartIndices();

    /** Precomputes the data. */
    protected abstract void computeWindowStatistics(boolean computeNormalizedValues);

    public abstract double[] getNormalizedValues(int windowStartIndex);
    public abstract double getSummedSquares(int windowStartIndex);

    /**
     * @return Whether the cross-correlation is to be run with the same parameters (windowSize, baseWindowOffset) on both time series.
     */
    public boolean sameCrossCorrelationParameters(AbstractWindowStatistics other){

        // both time series need to have the same length
        if(timeSeries.getSize() != other.timeSeries.getSize()) return false;
        // the same window size
        if(windowSize != other.windowSize) return false;
        // the same base window offset
        if(baseWindowOffset != other.baseWindowOffset) return false;

        return true;

    }

    public int[] getWindowStartIndices(){
        if(windowStartIndices == null) computeWindowStartIndices();
        return windowStartIndices;
    }
}
