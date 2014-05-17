package Data.Correlation;

import Data.TimeSeries;

/**
 * This class performs heavy precomputation of reusable terms to speed up cross-correlation computation.
 *
 * Base windows usually overlap, but apart from an incremental mean computation, this cannot be exploited for speedup,
 * since the normalized values change with the window mean (so summed squares can't be used neither).
 *
 * Example memory cost of computating all normalized values:
 * For a length of 10k values, and a window size of 1000, 64Mb are needed to store all normalized values.
 * 10k-1000+1 windows ~ 8k windows times 1000 values per window 8e6 times 8 byte per double = 64 Mb
 * The worst case is |w| = N/2 for which 200 Mb are needed.
 * Since this memory is used only during one iteration and freed afterwards, this should not be a problem.
 *
 * E.g. when cross correlating two sets of time series in a nested loop, this can be used for the time series used in the outer loop.
 * Created by Carl Witt on 14.05.14.
 */
public class BaseWindowStatistics extends AbstractWindowStatistics {

    /**
     * For the parameter documentation please refer to the documentation of the correspondent fields in {@link Data.Correlation.AbstractWindowStatistics}
     */
    protected BaseWindowStatistics(TimeSeries ts, int windowSize, int delta) {
        super(ts, windowSize, delta);

        int largestValidWindowStartIndex = ts.getSize() - windowSize; // N-windowSize+1 would be one-based

        // the number of base windows that completely fit in the time series (no shorter windows than |w|)
        numWindows = largestValidWindowStartIndex / delta + 1;    // +1: the first window is located at index zero

        // --------------------------------------------
        // precompute the actual means, summedSquares and
        // possibly normalized values for each window
        // --------------------------------------------

        computeWindowStatistics(true);

    }

    /**
     * Computes the mean and variance for each window and optionally the normalized values.
     * @param computeNormalizedValues whether to precompute the mean shifted values (memory expensive).
     */
    @Override
    protected void computeWindowStatistics(boolean computeNormalizedValues){

        // allocate memory
        means = new double[numWindows];
        summedSquares = new double[numWindows];
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

            // 3. compute variance
            summedSquares[i] = 0;
            for (int j = 0; j < windowSize; j++) {
                summedSquares[i] += normalizedWindowValues[j] * normalizedWindowValues[j];
            }

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
        return summedSquares[windowNumber];
    }

    @Override
    protected int getWindowNumberForStartIndex(int windowStartIndex){

        if(windowStartIndex % baseWindowOffset != 0) throw new AssertionError("There is no base window starting at time series value index "+windowStartIndex);
        return windowStartIndex/ baseWindowOffset;

    }

    @Override
    protected void computeWindowStartIndices(){

        windowStartIndices = new int[numWindows];

        for (int i = 0; i < numWindows; i++) {
            windowStartIndices[i] = i*baseWindowOffset;
        }

    }


}
