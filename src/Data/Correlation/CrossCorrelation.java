package Data.Correlation;

import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

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
     * Among many cross-correlation the means and rootOfSummedSquares, as well as the normalized windows can be reused.
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
    public static double correlationCoefficient(TimeSeries a, TimeSeries b, int from, int to, int tau){

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

        // the square root of the product of the rootOfSummedSquares (denominator term)
        double normalizationTerm = Math.sqrt( summedSquaresX * summedSquaresY );

        return covariance / normalizationTerm;

    }

    /**
     * Naive algorithm for two time series. Is used by {@link #naiveCrossCorrelation(Data.Windowing.WindowMetadata)} to compute the partial results.
     * @param metadata describes the computation input.
     * @return the windowed cross correlation between two time series.
     */
    private static CorrelationMatrix naiveCrossCorrelationAtomic(WindowMetadata metadata){

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
            result.append(result.new CorrelationColumnBuilder(baseWindowFrom, minTau).mean(means).standardDeviation(stdDevs).build());

            baseWindowFrom += metadata.baseWindowOffset;
        }

        return result;

    }

    public static CorrelationMatrix naiveCrossCorrelation(WindowMetadata metadata){

        final int numMatrices = metadata.setA.size() * metadata.setB.size();

        // compute one against one correlation matrices
        CorrelationMatrix[] partialResults = new CorrelationMatrix[numMatrices];
        int nextFreeSlot = 0;
        for(TimeSeries tsA : metadata.setA){
            for(TimeSeries tsB : metadata.setB){
                WindowMetadata partialMetadata = new WindowMetadata(tsA, tsB, metadata.windowSize, metadata.tauMin, metadata.tauMax, metadata.baseWindowOffset);
                CorrelationMatrix.setSignificanceLevel(partialMetadata, CorrelationMatrix.getSignificanceLevel(metadata));
                partialResults[nextFreeSlot++] = naiveCrossCorrelationAtomic(partialMetadata);
            }
        }

        CorrelationMatrix result = new CorrelationMatrix(metadata);

        // aggregate the results
        CorrelationSignificance tester = new CorrelationSignificance(metadata.windowSize, CorrelationMatrix.getSignificanceLevel(metadata));

        int n = partialResults.length;

        // for each column
        final int numColumns = partialResults[0].getSize();
        for (int colIdx = 0; colIdx < numColumns; colIdx++) {

            final CorrelationMatrix.CorrelationColumn representativeColumn = partialResults[0].getResultItems().get(colIdx);
            int colLen = representativeColumn.getSize();

            double[][] data = new double[CorrelationMatrix.NUM_STATS][colLen];

            // for each cell
            for (int lag = 0; lag < colLen; lag++) {

                // aggregate all correlations
                DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics();
                int numNegSig = 0, numPosSig = 0;
                for (int tsIdx = 0; tsIdx < numMatrices; tsIdx++) {
                    double r = partialResults[tsIdx].getResultItems().get(colIdx).data[CorrelationMatrix.MEAN][lag];
                    descriptiveStatistics.addValue(r);
                    if(tester.significanceTest(Math.abs(r))){
                        if(r < 0) numNegSig++;
                        else numPosSig++;
                    }
                }

                data[CorrelationMatrix.MEAN][lag] = descriptiveStatistics.getMean();
                data[CorrelationMatrix.STD_DEV][lag] = descriptiveStatistics.getStandardDeviation();
                data[CorrelationMatrix.MEDIAN][lag] = descriptiveStatistics.getPercentile(50);
                data[CorrelationMatrix.IQR][lag] = descriptiveStatistics.getPercentile(75)-descriptiveStatistics.getPercentile(25);
                data[CorrelationMatrix.NEGATIVE_SIGNIFICANT][lag] = (double) numNegSig / n;
                data[CorrelationMatrix.POSITIVE_SIGNIFICANT][lag] = (double) numPosSig / n;
                data[CorrelationMatrix.ABSOLUTE_SIGNIFICANT][lag] = data[CorrelationMatrix.NEGATIVE_SIGNIFICANT][lag] + data[CorrelationMatrix.POSITIVE_SIGNIFICANT][lag];

            }

            result.append(result.new CorrelationColumnBuilder(representativeColumn.windowStartIndex, representativeColumn.tauMin)
                    .mean(data[CorrelationMatrix.MEAN])
                    .standardDeviation(data[CorrelationMatrix.STD_DEV])
                    .median(data[CorrelationMatrix.MEDIAN])
                    .interquartileRange(data[CorrelationMatrix.IQR])
                    .negativeSignificant(data[CorrelationMatrix.NEGATIVE_SIGNIFICANT])
                    .positiveSignificant(data[CorrelationMatrix.POSITIVE_SIGNIFICANT])
                    .absoluteSignificant(data[CorrelationMatrix.ABSOLUTE_SIGNIFICANT])
                    .build()
                    );
        }

        return result;
    }

    /**
     * Computes the average of a window of a time series. This method doesn't perform any index checking.
     * @param a time series
     * @param from start index of the window (inclusive). Must be a valid index in the time series' data array.
     * @param to last index of the window (inclusive). Must be a valid index in the time series' data array.
     * @return the sum of all values in the window, divided by the number of values
     */
    public static double mean(TimeSeries a, int from, int to){

        // TODO: there was a case when an error was thrown here.
        assert from < to : String.format("Invalid window indices passed to mean computation. First window index %s needs to be smaller or equal to last window index %s.", from, to);
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
     * @param previousMean the mean of the previous window. pass Double.NaN to indicate, that no previous result is available.
     * @param previousFrom the first index of the previous window. the index is assumed to be smaller than {@code from}.
     *                     the last index of the previous window is derived from the assumption that the last window has the same length as the current window.
     * @return the result equals the sum of all values in the window, divided by the number of values
     */
    public static double incrementalMean(TimeSeries a, int from, int to, double previousMean, int previousFrom){

        if(Double.isNaN(previousMean)) return mean(a, from, to);

        assert from <= to : String.format("Window start index %s needs to be ≤ window end index %s.",from, to);
        assert previousFrom < from : String.format("Previous window start index %s needs to be smaller than the windows start index %s", previousFrom, from);

        double[] x = a.getDataItems().im;
        assert to < x.length : "Range end is outside time series.";

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

    /**
     * Subtracts the mean of the given window from each value in the window.
     * @param data values
     * @param from range start index within data
     * @param to   range end index (inclusive) within data
     * @param mean the mean within the window (can usually be computed with a rolling mean algorithm to save time.)
     * @param out  the array where to put the data. needs to be of appropriate size. this way, garbage collection time can be reduced significantly.
     */
    public static void normalizeValues(double[] data, int from, int to, double mean, double[] out){
        int target = 0;
        for (int i = from; i <= to; i++, target++) out[target] = data[i]-mean;
    }

    public static double rootOfSummedSquares(double[] normalizedValues) {
        double sum = 0;
        for (double normalizedValue : normalizedValues)
            sum += normalizedValue * normalizedValue; // square
        return Math.sqrt(sum);
    }

}
