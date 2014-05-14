package Data.Correlation;

import Data.TimeSeries;

/**
 * Using this class, reusable parts of the cross-correlation computation can be precomputed at the expense of memory.
 * The object calculates and stores the mean and variance of the windows that occur in the cross-correlation computation.
 * Depending on the constructor arguments, the mean-shifted values of the time series in each window can be precomputed,
 * which requires a lot of memory.
 *
 * Definitions:
 *   - A window of a time series is a continuous subsequence of time series.
 *      All windows have the same length |w|.
 *      The precomputed windows are enumerated with an index from 1 to l.
 *      The start index of the i-th window is denoted by s_i.
 *
 *   - A base window has s_i = k*delta (k = 0,1,2,...).
 *   - A lag window has s_i = s_b + tau (tau = tauMin, tauMin+1, ..., tauMax), where s_b = k*delta.
 *      The range [s_b + tauMin ... s_b + tauMax] is called the lag range for base window b.
 *   - A shared lag window is a window that belongs to more than one base window.
 *      Example: delta = 1. A window that starts at index 1 can be assigned the base window starting at index 0 (tau = 1),
 *      or the base window starting at index 1 (tau = 0) or at index 2 (tau = -1), etc.
 *
 * Created by Carl Witt on 13.05.14.
 */
public class StatisticsForWindows {

    /**
     * Base windows have overlap, but apart from the incremental mean, this cannot be used, since the normalized values change with the window mean (so summed squares can't be used neither).
     *
     * When doing the cross-correlation, the slower outer loop repeatedly uses the current time series A.
     * Reusable terms: window means (memory: l) and normalized values (memory: approx. |w| * N)
     * Storing both in memory is probably a good idea.
     *
     * The faster inner loop touches each time series in set B only once.
     * Reusable terms: Entire lag windows (summed squares and normalized values) between subsequent base windows.
     * Storing all of them requires way too much space. But keeping those in memory that can be reused for the next base window is fast and memory efficient.
     * Alternatively, precomputing all lag windows and deleting the normalized values afterwards is less complicated and requires probably not too much memory.
     *
     * Memory cost of computating all normalized values:
     * For a length of 10k values, and a window size of 1000, 64Mb are needed to store all normalized values.
     * 10k-1000+1 windows ~ 8k windows times 1000 values per window 8e6 times 8 byte per double = 64 Mb
     * The worst case is |w| = N/2 for which 200 Mb are needed.
     * Since this memory is used only during one iteration and freed afterwards, this should not be a problem.
     *
     */

    /** Describes whether the last few reusable parts of the cross-correlation computation are held in memory (CACHE) or
     * whether all reusable parts are stored in memory (MATERIALIZE).
     * CACHE is good for iterating a single time over a time series (not all precomputed data memory
      */
//    enum MemoryStrategy {
//        CACHE,
//        MATERIALIZE
//    }
//    enum Depth {
//        ONLY_BASE_WINDOWS,
//        BASE_AND_LAG_WINDOWS,
//    }

    // -----------------------------------------------------------------------------------------------------------------
    // computation specific parameters
    // -----------------------------------------------------------------------------------------------------------------

    final TimeSeries timeSeries;

    /** Size of the windows that are taken from a time series (denoted |w|). */
    final int windowSize;

    /** The minimum and maximum desired time lag in the cross-correlation computation. */
    final int tauMin, tauMax;

    /** The offset between base windows. delta = s_b' - s_b where b is any base window and b' its subsequent base window. */
    final int delta;




    // -----------------------------------------------------------------------------------------------------------------
    // derived from specific parameters
    // -----------------------------------------------------------------------------------------------------------------

    /** Total number of windows to precompute, assuming only complete windows are considered. */
    final int numWindows;

    /** Zero based indices of the values in the time series where the first and last lag window start. */
    int firstLagWindowStart, lastLagWindowStart;

    /** The i-th array entry contains the start index (zero based) in the time series for the (i+1)-th window (because windows are enumerated 1-based) to precompute. */
    int[] windowStartIndices;

    /** Number of values that are shared between two consecutive base windows. */
    final int baseWindowOverlap;

    /** Number of lag windows that can be used in the cross-correlation computation of a base window as well as in the computation of its subsequent base window. */
    final int sharedLagWindows;

    /** If true, the start indices of any two consecutive precomputed windows always differ by one.
     * This might not be the case if the delta is large, because then there are "holes" between the lag ranges.
     * In this case, some subsequences of the time series are NOT needed for any cross-correlation computation step.
     */
    final boolean continuousIndices;

    // -----------------------------------------------------------------------------------------------------------------
    // precomputed data (public because hopefully faster with direct access than with accessor functions)
    // -----------------------------------------------------------------------------------------------------------------

    /** The mean of window i. */
    public double[] means;
    /** The sum of the squared normalized values in window i. (x_i - average(window i))^2 */
    public double[] summedSquares;
    /** The mean-shifted values of window i (x - average of window i). 
     * Note that the shared values between windows can not be reused since the means of the windows vary. */
    protected double[][] normalizedValues;

    // -----------------------------------------------------------------------------------------------------------------
    // methods
    // -----------------------------------------------------------------------------------------------------------------

    public static StatisticsForWindows statisticsForBaseWindows(){

        throw new UnsupportedOperationException("Factory method not yet implemented.");

    }
    /**
     * For the parameter documentation please refer to the documentation of the correspondent fields.
     * @param computeNormalizedValues whether to precompute the mean shifted values (memory expensive).
     */
    protected StatisticsForWindows(TimeSeries ts, int windowSize, int delta, int tauMin, int tauMax, boolean computeNormalizedValues) {
        
        this.timeSeries = ts;
        this.windowSize = windowSize;
        this.delta = delta;
        if(delta == 0) throw new AssertionError("Base window offset (delta) must be larger than zero.");
        this.tauMin = tauMin;
        this.tauMax = tauMax;

        // time series length
        int N = ts.getSize();

        // the number of shared values between two consecutive base windows
        baseWindowOverlap = windowSize - delta;

        /** The number of lag windows that are needed both in the cross-correlation computation of a base window b and its subsequent base window b'.
         * This is the size of the intersection of the ranges [s_b + tauMin ... s_b + tauMax] and [s_b' + tauMin ... s_b' + tauMax]. */
        sharedLagWindows = tauMax - tauMin - delta + 1;

        // if there are shared windows, the lag ranges are not disjoint and thus their union is a continuous sequence.
        // if the distance between subsequent lag ranges is 1 (shared windows = 0) all lag ranges are adjacent and their union is again a continous index range.
        // if there distance between subsequent lag ranges is > 1 there are "holes" between the lag ranges and their union is not a continuous index subsequence.
        continuousIndices = sharedLagWindows > -1;

        // the number of complete base windows that fit in the time series
        int baseWindowGuess = N / delta;    // since windows can overlap, each window needs effectively space delta
        int leftOver = N % delta;           // unused space at the end of the time series
        // check whether the last window overlaps the end of the time series.
        int baseWindows = baseWindowOverlap > leftOver ? baseWindowGuess - 1 : baseWindowGuess;

        // compute the total number of complete windows (length = |w|) that fit in the time series
        int lastBaseWindowStart = (baseWindows-1) * delta;                              // zero based index
        lastLagWindowStart = Math.min(N-windowSize,lastBaseWindowStart+tauMax);     // zero based index
        if(continuousIndices){
            // the number of windows results from the length of the range between s_1 and s_l where s_1 is the start index of the first lag window and s_l is the start index of the last lag window.
            firstLagWindowStart = Math.max(0, tauMin);
            numWindows = lastLagWindowStart - firstLagWindowStart + 1;
        } else {
            // the number of windows results from the sum of all lag ranges plus the base windows (if not already included in the lag ranges)
            int windowsPerLagRange = tauMax - tauMin + 1;
            int windowsFirstLagRange = tauMax - Math.max(0, tauMin) + 1; // the first window starts at 0 so negative lags are omitted
            int windowsLastRange = lastLagWindowStart-lastBaseWindowStart  - tauMin + 1;
            // if the base window start index is included in the lag range
            if(tauMin <= 0 && tauMax >= 0){
                numWindows = windowsFirstLagRange + (baseWindows-2)*windowsPerLagRange + windowsLastRange;
            } else {
                // if not, add the base windows.
                numWindows = windowsFirstLagRange + (baseWindows-2)*windowsPerLagRange + windowsLastRange + baseWindows;
            }
        }

        // --------------------------------------------
        // precompute the actual means, summedSquares and
        // possibly normalized values for each window
        // --------------------------------------------

        computeWindowStatistics(computeNormalizedValues);

    }

    /**
     * Computes the mean and variance for each window and optionally the normalized values.
     * @param computeNormalizedValues whether to precompute the mean shifted values (memory expensive).
     */
    private void computeWindowStatistics(boolean computeNormalizedValues){

        means = new double[numWindows];
        summedSquares = new double[numWindows];
        if(computeNormalizedValues) normalizedValues = new double[numWindows][];

        double[] values = timeSeries.getDataItems().im;

        int[] startIndices = getWindowStartIndices();

        // for each window to precompute
        for (int i = 0; i < numWindows; i++) {

            // INV: mean contains the average of the values in the current (i-th) window
            if(i == 0){
                means[i] = CrossCorrelation.mean(timeSeries, startIndices[0], startIndices[0] + windowSize - 1);
            } else {
                means[i] = CrossCorrelation.incrementalMean(timeSeries, windowStartIndices[i], windowStartIndices[i]+windowSize-1, means[i-1], windowStartIndices[i-1]);
            }

            // 1. normalize window values by subtracting the mean
            double[] normalizedWindowValues = new double[windowSize];
            for (int j = 0; j < windowSize; j++) {
                normalizedWindowValues[j] = values[j+windowStartIndices[i]] - means[i];
            }
            // cache values if allowed
            if(computeNormalizedValues) normalizedValues[i] = normalizedWindowValues;

            // 2. compute variance
            summedSquares[i] = 0;
            for (int j = 0; j < windowSize; j++) {
                summedSquares[i] += normalizedWindowValues[j] * normalizedWindowValues[j];
            }

            // 3. update mean incrementally

        }
        
    }

    /**
     * These values are cached only if the computeNormalizedValues parameter is true in the constructor. This is because it usually requires a lot of memory.
     * @param windowStartIndex The zero based index of the value in the time series where the window starts.
     * @return the mean-shifted values ( x_i - average(window) ) of a window
     */
    public double[] getNormalizedValues(int windowStartIndex){
        /** These values are not cached because they usually require a lot of memory. */
        if(normalizedValues != null) return normalizedValues[windowStartIndex];

        int windowNumber = getWindowNumberForStartIndex(windowStartIndex);

        double normalizedWindowValues[] = new double[windowSize];
        for (int j = 0; j < windowSize; j++) {
            normalizedWindowValues[j] = timeSeries.getDataItems().im[j+windowStartIndices[windowNumber]] - means[windowNumber];
        }

        return normalizedWindowValues;
    }

    public int[] getWindowStartIndices(){
        if(windowStartIndices == null) computeWindowStartIndices();
        return windowStartIndices;
    }

    /**
     * @param windowStartIndex the zero based index of the time series value where the windows starts.
     * @return the zero based window index
     */
    protected int getWindowNumberForStartIndex(int windowStartIndex){

        if(! continuousIndices) throw new UnsupportedOperationException("Window number from start index computation not yet implemented for non continuous lag ranges.");

        return windowStartIndex - firstLagWindowStart;

    }

    protected void computeWindowStartIndices(){

        if(! continuousIndices) throw new UnsupportedOperationException("Window start index computation not yet implemented for non continuous lag ranges.");

        windowStartIndices = new int[numWindows];

        for (int i = 0; i < numWindows; i++) {
            windowStartIndices[i] = firstLagWindowStart+i;
        }

        if (windowStartIndices[numWindows-1] != lastLagWindowStart) throw new AssertionError("Fenster gehen nicht auf.");
    }

    /**
     * @return Whether the cross-correlation is to be run with the same parameters (windowSize, delta) on both time series.
     */
    public boolean sameCrossCorrelationParameters(StatisticsForWindows other){

        // both time series need to have the same length
        if(timeSeries.getSize() != other.timeSeries.getSize()) return false;
        // the same window size
        if(windowSize != other.windowSize) return false;
        // the same lag range
        if(tauMin != other.tauMin || tauMax != other.tauMax) return false;
        // the same base window offset
        if(delta != other.delta) return false;

        return true;

    }

}
