package Data.Windowing;

import Data.TimeSeries;

/**
 * Using implementations of this class, reusable terms of the cross-correlation computation can be precomputed at the expense of memory.
 * There's the {@link BaseWindowStatistics} class for heavy precompution of data and the {@link LagWindowStatistics} class
 * for lightweight precomputation of data and on-the-fly caching of reusable values.
 *
 * The reusable terms comprise normalized values and the root of the summed squares.
 *
 * Normalized values:
 * The mean-shifted values are needed to compute the covariance. Reusing them should be saving lots of subtractions, since
 * each time series value needs to be mean-shifted and lag windows are usually targeted from several base windows.
 * On the other hand, this comes at relatively high memory cost. Example memory cost of computing all normalized values:
 * For a length of 10k values, and a window size of 1000, 64Mb are needed to store all normalized values.
 * 10k-1000+1 windows ~ 8k windows times 1000 values per window 8e6 times 8 byte per double = 64 Mb
 * The worst case is |w| = N/2 for which 200 Mb are needed.
 * Since this memory is used only during one iteration and freed afterwards, this should not be a problem.
 *
 * Root of summed squares:
 * The root of the sum of the squares of the normalized values is needed to normalize the covariance (denominator)
 *      sqrt((x_i - average(window i))^2)
 * Reusing this term saves |w| square operations, |w| additions and one square root computation.
 *
 * Means:
 * Since base and lag windows usually overlap, the means can be computed much faster by using an incremental mean formula.
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
    public final int windowSize;

    /** The offset between base windows. baseWindowOffset = s_b' - s_b where b is any base window and b' its subsequent base window. */
    final int baseWindowOffset;

    // -----------------------------------------------------------------------------------------------------------------
    // derived from specific parameters
    // -----------------------------------------------------------------------------------------------------------------

    /** Total number of windows to precompute, only complete windows are considered. */
    public int numWindows;

    /** The mean of window i. */
    public double[] means;

    /** The square root of the sum of the squared normalized values in window i. sqrt((x_i - average(window i))^2) */
    public double[] rootOfSummedSquares;

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
    public abstract int getWindowNumberForStartIndex(int windowStartIndex);

    protected abstract void computeWindowStartIndices();

    /** Precomputes the data. */
    public abstract void computeWindowStatistics(boolean computeNormalizedValues);

    public abstract double[] getNormalizedValues(int windowStartIndex);
    public abstract double getSummedSquares(int windowStartIndex);

    /**
     * @return Whether the cross-correlation is to be run with the same parameters (windowSize, baseWindowOffset) on both time series.
     */
    boolean sameCrossCorrelationParameters(AbstractWindowStatistics other){

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
