package Data.Windowing;

import Data.Correlation.CrossCorrelation;
import Data.TimeSeries;

/**
 * Leightweigt, on-the-fly computation and caching of reusable terms in the cross-correlation formula.
 * All indices are zero-based.
 *
 * Created by Carl Witt on 13.05.14.
 * @deprecated  use the {@link Data.Correlation.LagWindowCache} for exploiting precomputed values for correlation computation
 */
@Deprecated
public class LagWindowStatistics extends AbstractWindowStatistics {

    /** The minimum and maximum desired time lag in the cross-correlation computation. */
    private final int tauMin;
    private final int tauMax;


    // -----------------------------------------------------------------------------------------------------------------
    // derived from specific parameters
    // -----------------------------------------------------------------------------------------------------------------

    /** Indices of the time series values where the first and last lag window start. */
    private int firstLagWindowStart;
    private final int lastLagWindowStart;

    /** Number of lag windows that can be used in the cross-correlation computation of a base window as well as in the computation of its subsequent base window. */
    public final int sharedLagWindows;

    /**
     * If true, the start indices of any two consecutive precomputed windows always differ by one.
     * This might not be the case if the baseWindowOffset is large, because then there are "holes" between the lag ranges.
     * In this case, some subsequences of the time series are NOT needed for any cross-correlation computation step.
     */
    public final boolean continuousIndices;

    // -----------------------------------------------------------------------------------------------------------------
    // methods
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * For the parameter documentation please refer to the documentation of the correspondent fields.
     */
    public  LagWindowStatistics(TimeSeries ts, int windowSize, int baseWindowOffset, int tauMin, int tauMax) {
        super(ts, windowSize, baseWindowOffset);

        this.tauMin = tauMin;
        this.tauMax = tauMax;

        // time series length
        int N = ts.getSize();

        /** The number of lag windows that are needed both in the cross-correlation computation of a base window b and its subsequent base window b'.
         * This is the size of the intersection of the ranges [s_b + tauMin ... s_b + tauMax] and [s_b' + tauMin ... s_b' + tauMax]. */
        sharedLagWindows = tauMax - tauMin - baseWindowOffset + 1;

        // if there are shared windows, the lag ranges are not disjoint and thus their union is a continuous sequence.
        // if the distance between subsequent lag ranges is 1 (shared windows = 0) all lag ranges are adjacent and their union is again a continous index range.
        // if there distance between subsequent lag ranges is > 1 there are "holes" between the lag ranges and their union is not a continuous index subsequence.
        continuousIndices = sharedLagWindows > -1;

        // compute number of base windows (that completely fit in the time series: no shorter windows than |w|)
        int largestValidWindowStartIndex = ts.getSize() - windowSize; // N-windowSize+1 would be one-based
        int baseWindows = largestValidWindowStartIndex / baseWindowOffset + 1;    // +1: the first window is located at index zero

        // compute the total number of complete windows (length = |w|) that fit in the time series
        int lastBaseWindowStart = (baseWindows-1) * baseWindowOffset;
        lastLagWindowStart = Math.min(N-windowSize,lastBaseWindowStart+tauMax);
        if(continuousIndices){
            // the number of windows results from the length of the range between s_1 and s_l where s_1 is the start index of the first lag window and s_l is the start index of the last lag window.
            firstLagWindowStart = Math.max(0, tauMin);
            numWindows = lastLagWindowStart - firstLagWindowStart + 1;
        } else {
            // the number of windows results from the sum of all lag ranges plus the base windows (if not already included in the lag ranges)
            int windowsPerLagRange = tauMax - tauMin + 1;
            int windowsFirstLagRange = tauMax - Math.max(0, tauMin) + 1; // the first window starts at least at index 0
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
        // precompute the actual means, rootOfSummedSquares and
        // possibly normalized values for each window
        // --------------------------------------------

        computeWindowStatistics(true);

    }

    /**
     * Computes the mean and variance for each window and optionally the normalized values.
     * @param computeNormalizedValues whether to precompute the mean shifted values (memory expensive).
     */
    public void computeWindowStatistics(boolean computeNormalizedValues){

        // allocate memory
        means = new double[numWindows];
        rootOfSummedSquares = new double[numWindows];
        normalizedValues = new double[numWindows][];

        double[] values = timeSeries.getDataItems().im;

        // iterate over windows
        int[] startIndices = getWindowStartIndices();
        for (int i = 0; i < numWindows; i++) {

            // 1. compute mean (the first one naively and all subsequent incrementally)
            if(i == 0){
                means[i] = CrossCorrelation.mean(timeSeries, startIndices[0], startIndices[0] + windowSize - 1);
            } else {
                means[i] = CrossCorrelation.incrementalMean(timeSeries,
                        windowStartIndices[i],                  // from
                        windowStartIndices[i] + windowSize - 1,     // to
                        means[i - 1],                             // previous mean
                        windowStartIndices[i - 1]);               // previous window start index
            }

            // 2. normalize window values by subtracting the mean (this is necessary to compute the
            double[] normalizedWindowValues = new double[windowSize];
            for (int j = 0; j < windowSize; j++) {
                normalizedWindowValues[j] = values[j+windowStartIndices[i]] - means[i];
            }
            if(computeNormalizedValues)
                normalizedValues[i] = normalizedWindowValues;   // cache values if allowed

            // 3. compute variance
            rootOfSummedSquares[i] = 0;
            for (int j = 0; j < windowSize; j++) {
                rootOfSummedSquares[i] += normalizedWindowValues[j] * normalizedWindowValues[j];
            }
            rootOfSummedSquares[i] = Math.sqrt(rootOfSummedSquares[i]);

        }

    }

    @Override
    public double[] getNormalizedValues(int windowStartIndex) {

        int windowNumber = getWindowNumberForStartIndex(windowStartIndex);

        /** These values are not cached because they usually require a lot of memory. */
        if(normalizedValues != null) return normalizedValues[windowNumber];

        throw new UnsupportedOperationException("On demand computation of normalized values not yet implemented for lag window statistics.");

    }

    @Override
    public double getSummedSquares(int windowStartIndex) {
        throw new UnsupportedOperationException("!");
    }


    public int[] getWindowStartIndices(){
        if(windowStartIndices == null) computeWindowStartIndices();
        return windowStartIndices;
    }

    public int getWindowNumberForStartIndex(int windowStartIndex){

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
     * @return Whether the cross-correlation is to be run with the same parameters (windowSize, baseWindowOffset) on both time series.
     */
    public boolean sameCrossCorrelationParameters(LagWindowStatistics other){

        if( ! super.sameCrossCorrelationParameters(other) ) return false;

        if(tauMin != other.tauMin || tauMax != other.tauMax) return false;

        return true;

    }


}
