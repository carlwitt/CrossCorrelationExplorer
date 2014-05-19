package Data.Correlation;

import Data.TimeSeries;

import java.util.ArrayList;


/**
 * Contains the algorithms to compute normalized windowed cross correlation between time series.
 * Created by Carl Witt
 */
public class CrossCorrelation {


    /**
     *
     * Preprocess time series B under consideration, i.e. calculate mean and variance for all used windows.
     * Within a single cross-correlation, the only improvement for time series A is an incremental mean.
     * Among many cross-correlation the means and summedSquares, as well as the normalized windows can be reused.
     *
     * Caching normalized values:
     * An improvement that can be evaluated later is to cache the normalized values for the time series from set B, too but
     * only on a sliding window basis, i.e. keep only those full windows in memory which are needed for the computation of the next base window.
     * This potentially saves a lot of operations, but only subtractions, as far as I see.
     * A profiler might be able to give detailed information about the optimization potential.
     *
     * An object containing the precomputed data might be useful. It can be easily discarded to free memory.
     * Since all precomputed data is specific to a certain window size this might an even cleaner solution.
     */

    public static enum NA_ACTION{
        //        NA_FAIL,
        REPLACE_WITH_ZERO,
        LEAVE_UNCHANGED
    }

    public static CorrelationMatrix multiCrossCorrelation(CorrelationMetadata metadata){

        /**
         * The slow running outer loop repeatedly uses tsA.
         * Reusable terms: normalized values (memory: approx. |w| * N) and summed squares (memory: l)
         * Both is stored in the BaseWindowStatistics.
         *
         * The faster inner loop touches each tsB only once for each tsA.
         * Reusable terms: Entire lag windows (summed squares and normalized values) between subsequent base windows.
         * Storing all of them requires way too much space. But keeping those in memory that can be reused for the next base window is fast and memory efficient.
         * Alternatively, precomputing all lag windows and deleting the normalized values afterwards is less complicated and requires probably not too much memory.
         */
        // precompute lightweight (only means and summedSquares) window statistics for each time series in set B

        for (TimeSeries tsA : metadata.setA){

            // precompute data for current time series
            BaseWindowStatistics precomputationA = new BaseWindowStatistics(tsA, metadata.windowSize, metadata.baseWindowOffset);


            for (TimeSeries tsB : metadata.setB){




            }

        }

        throw new UnsupportedOperationException("Not yet implemented.");

    }

    public static CorrelationMatrix crossCorrelation(BaseWindowStatistics a, LagWindowStatistics b, NA_ACTION naAction){

        CorrelationMatrix result = new CorrelationMatrix(new CorrelationMetadata(a.timeSeries, b.timeSeries, a.windowSize, b.tauMin, b.tauMax, naAction, 1));

        // check whether time series length, window size, baseWindowOffset, etc. matches
        if( ! a.sameCrossCorrelationParameters(b) ) throw new AssertionError("Precomputed data for time series a and time series b is incompatible.");

        for (int i = 0; i < a.numWindows; i++) {

            // add one column for each base window
            // if ¬ hasWindow(lagWindowIndex) lagWindowCache.put(...)

        }

        throw new UnsupportedOperationException("Not yet implemented.");
//        return result;

    }


    /**
     * Computes the pearson correlation coefficient.
     * Naïve reference implementation for testing.
     * @param a first time series
     * @param b second time series
     * @param from window start index (inclusive) in first time series
     * @param to window end index (inclusive) in first time series
     * @param tau the window start and end indices in the second time series result from adding tau to the indices in the first time series.
     * @return normalized cross correlation between the two windows
     */
    protected static double correlationCoefficient(TimeSeries a, TimeSeries b, int from, int to, int tau){

        // time series data
        double[] x = a.getDataItems().im,
                 y = b.getDataItems().im;

        // mean of first and second window
        double meanX = mean(a, from, to),
               meanY = mean(b, from+tau, to+tau);

        // the sum of pointwise multiplied normalized measurements (enumerator term)
        double covariance = 0;
        // sum of squared normalized values of both windows
        double summedSquaresX = 0,
               summedSquaresY = 0;

        for (int i = from; i <= to; i++) {

            double normalizedX = x[i] - meanX;
            double normalizedY = y[i+tau] - meanY;

            covariance += normalizedX * normalizedY;

            summedSquaresX += normalizedX * normalizedX;
            summedSquaresY += normalizedY * normalizedY;
        }

        // the square root of the product of the summedSquares (denominator term)
        double normalizationTerm = Math.sqrt( summedSquaresX * summedSquaresY );

        return covariance / normalizationTerm;

    }

    public static CorrelationMatrix naiveCrossCorrelation(CorrelationMetadata metadata){

        CorrelationMatrix result = new CorrelationMatrix(metadata);

        if(metadata.setA.size() > 1 || metadata.setB.size() > 1) throw new AssertionError("Pass two time series!");

        TimeSeries tsA = metadata.setA.get(0);
        TimeSeries tsB = metadata.setB.get(0);

        int baseWindowFrom = 0;

        while(baseWindowFrom + metadata.windowSize-1 < tsA.getSize()){

            ArrayList<Double> correlationCoefficients = new ArrayList<>();
            int lagWindowFrom = Math.max(0, baseWindowFrom + metadata.tauMin);
            int tau = lagWindowFrom - baseWindowFrom;
            int minTau = tau;
            while(baseWindowFrom + tau + metadata.windowSize-1 < tsB.getSize() && tau <= metadata.tauMax){

                correlationCoefficients.add(correlationCoefficient(tsA, tsB, baseWindowFrom, baseWindowFrom+ metadata.windowSize-1, tau));
                tau++;
            }

            // copy means from computed list to array
            double[] means = new double[correlationCoefficients.size()];
            for (int i = 0; i < means.length; i++) means[i] = correlationCoefficients.get(i);
            // create 0 values standard deviation array
            double[] stdDevs = new double[means.length];
            CorrelationMatrix.Column column = result.new Column(means, stdDevs, baseWindowFrom, minTau);
            result.append(column);

            baseWindowFrom += metadata.baseWindowOffset;
        }

        return result;

    }

    /**
     * Computes the normalized cross-correlation between two windows of two time series.
     * @param a Precomputed data for the first time series.
     * @param b Precomputed data for the second time series.
     * @param windowStartIndexA The zero based index of the value where the window in the first time series starts.
     * @param tau The offset (relative to windowStartIndexA) of the window in the second time series.
     * @return Normalized correlation coefficient.
     */
    protected static double correlationCoefficient(LagWindowStatistics a, LagWindowStatistics b, int windowStartIndexA, int tau){

        int windowStartIndexB = windowStartIndexA + tau;

        int windowNumberA = a.getWindowNumberForStartIndex(windowStartIndexA);
        int windowNumberB = b.getWindowNumberForStartIndex(windowStartIndexB);

        // summedSquares of both windows
        double summedSquaresA = a.summedSquares[windowNumberA],
               summedSquaresB = b.summedSquares[windowNumberB];

        double[] normalizedValuesA = a.getNormalizedValues(windowStartIndexA),
                 normalizedValuesB = b.getNormalizedValues(windowStartIndexB);

        // the sum of pointwise multiplied normalized measurements (enumerator term)
        double covariance = 0;
        for (int i = 0; i < a.windowSize; i++) {
            covariance += normalizedValuesA[i] * normalizedValuesB[i];
        }

        // the square root of the product of the summedSquares (denominator term)
        double normalizationTerm = Math.sqrt( summedSquaresA * summedSquaresB );

        return covariance / normalizationTerm;

    }

    /**
     * Computes the average of a window of a time series. This method doesn't perform any index checking.
     * @param a time series
     * @param from start index of the window (inclusive). Must be a valid index in the time series' data array.
     * @param to last index of the window (inclusive). Must be a valid index in the time series' data array.
     * @return the sum of all values in the window, divided by the number of values
     */
    protected static double mean(TimeSeries a, int from, int to){

        if (to < from) throw new AssertionError("Invalid window indices passed to mean computation. First window index needs to be smaller or equal to last window index.");
        double mean = 0;
        for (int i = from; i <= to; i++) {
            mean += a.getDataItems().im[i];
        }
        return mean / (to-from+1);
    }

    /**
     * Calculates the mean of window of a time series from a previously calculated mean of an equal sized window.
     * @param a time series
     * @param from start index of the window (inclusive). Must be a valid index in the time series' data array.
     * @param to last index of the window (inclusive). Must be a valid index in the time series' data array.
     * @param previousMean the mean of the previous window.
     * @param previousFrom the first index of the previous window. the index is assumed to be smaller than {@code from}.
     *                     the last index of the previous window is derived from the assumption that the last window has the same length as the current window.
     * @return the result equals the sum of all values in the window, divided by the number of values
     */
    protected static double incrementalMean(TimeSeries a, int from, int to, double previousMean, int previousFrom){


        if (to < from) throw new AssertionError("Invalid window indices passed to incremental mean computation. First window index needs to be smaller or equal to last window index.");
        if (previousFrom >= from) throw new AssertionError("Invalid previous first window index. Needs to be smaller than the windows first index.");

        double[] x = a.getDataItems().im;
        if (to >= x.length ) throw new AssertionError("Range end is outside time series.");

        // window width
        int n = to - from + 1;
        int previousTo = previousFrom + n-1;

        // fallback to simple mean calculation if less than half of the values from the previous result can be reused
        int reusableValues = previousTo - from + 1;
        if(reusableValues < n/2) return mean(a, from, to);

        // subtract those elements that are not within the current window
        // first summing and then dividing avoids the costly division operation on each element
        double subtraction = 0;
        for (int i = previousFrom; i <= Math.min(from-1, previousTo) ; i++) { // in case previousTo is smaller than from, the windows don't overlap, but the result will be still correct.
            subtraction -= x[i];
        }
        previousMean += subtraction/n;

        // add those elements that are exclusive to the new window
        double addition = 0;
        for (int i = Math.max(from, previousTo+1); i <= to; i++) {
            addition += x[i];
        }
        return previousMean + (addition/n);

    }

}
