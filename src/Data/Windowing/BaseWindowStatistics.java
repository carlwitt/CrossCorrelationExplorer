package Data.Windowing;

import Data.Correlation.CrossCorrelation;
import Data.TimeSeries;

/**
 * This class performs heavy precomputation of reusable terms to speed up cross-correlation computation.
 *
 * E.g. when cross correlating two sets of time series in a nested loop, this can be used for the time series
 * used in the outer, slow running loop.
 *
 * Created by Carl Witt on 14.05.14.
 * @deprecated  use the {@link Data.Correlation.LagWindowCache} for exploiting precomputed values for correlation computation
 */
@Deprecated
public class BaseWindowStatistics extends AbstractWindowStatistics {

    /**
     * For the parameter documentation please refer to the documentation of the correspondent fields in {@link AbstractWindowStatistics}
     */
    public BaseWindowStatistics(TimeSeries ts, int windowSize, int delta) {
        super(ts, windowSize, delta);

        int largestValidWindowStartIndex = ts.getSize() - windowSize; // N-windowSize+1 would be one-based

        // the number of base windows that completely fit in the time series (no shorter windows than |w|)
        numWindows = largestValidWindowStartIndex / delta + 1;    // +1: the first window is located at index zero

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
    @Override
    public void computeWindowStatistics(boolean computeNormalizedValues){

        // allocate memory
        means = new double[numWindows];
        rootOfSummedSquares = new double[numWindows];
        if(computeNormalizedValues) normalizedValues = new double[numWindows][];

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
                        windowStartIndices[i]+windowSize-1,     // to
                        means[i-1],                             // previous mean
                        windowStartIndices[i-1]);               // previous window start index
            }

            // 2. normalize window values by subtracting the mean (this is necessary to compute the
            double[] normalizedWindowValues = new double[windowSize];
            for (int j = 0; j < windowSize; j++) {
                normalizedWindowValues[j] = values[j+windowStartIndices[i]] - means[i];
            }
            if(computeNormalizedValues)
                normalizedValues[i] = normalizedWindowValues;   // cache values if allowed

            // 3. compute root of summed squares
            rootOfSummedSquares[i] = 0;
            for (int j = 0; j < windowSize; j++) {
                rootOfSummedSquares[i] += normalizedWindowValues[j] * normalizedWindowValues[j];
            }
            rootOfSummedSquares[i] = Math.sqrt(rootOfSummedSquares[i]);

        }

    }

    /**
     * These values are cached only if the computeNormalizedValues parameter is true in the constructor. This is because it usually requires a lot of memory.
     * @param windowStartIndex The zero based index of the value in the time series where the window starts.
     * @return the mean-shifted values ( x_i - average(window) ) of a window
     */
    @Override
    public double[] getNormalizedValues(int windowStartIndex){

        int windowNumber = getWindowNumberForStartIndex(windowStartIndex);

        /** These values are not cached because they usually require a lot of memory. */
        if(normalizedValues != null) return normalizedValues[windowNumber];


        double normalizedWindowValues[] = new double[windowSize];
        for (int j = 0; j < windowSize; j++) {
            normalizedWindowValues[j] = timeSeries.getDataItems().im[j+windowStartIndices[windowNumber]] - means[windowNumber];
        }

        return normalizedWindowValues;
    }

    /**
     * Returns the sum of the squared mean shifted values in the window starting at the given index.
     * @param windowStartIndex The zero based index of the value in the time series where the window starts.
     * @return ∑( x_i - µ )^2
     */
    @Override
    public double getSummedSquares(int windowStartIndex){

        int windowNumber = getWindowNumberForStartIndex(windowStartIndex);
        return rootOfSummedSquares[windowNumber];
    }

    @Override
    public int getWindowNumberForStartIndex(int windowStartIndex){

        if(windowStartIndex % baseWindowOffset != 0) throw new AssertionError("There is no base window starting at time series value index "+windowStartIndex);
        return windowStartIndex/ baseWindowOffset;

    }

    @Override
    public void computeWindowStartIndices(){

        windowStartIndices = new int[numWindows];

        for (int i = 0; i < numWindows; i++) {
            windowStartIndices[i] = i*baseWindowOffset;
        }

    }


}
