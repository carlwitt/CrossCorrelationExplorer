package Data.Correlation;

import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.function.Function;

/**
 * Contains algorithms to compute Norbert Marwan's symmetric variant of the cross correlation matrix.
 * The matrix is made symmetric by shifting the second time series Y for time lags ≥ 0 and shifting the
 * first time series X for time lags < 0. This way an even rastering of the windows is achieved.
 *
 * Contains utility methods for incremental mean computation, normalization and summed squares.
 *
 * Created by Carl Witt on 09.08.14.
 */
public class CrossCorrelation {

    /**
     * Naive windowed cross correlation algorithm for two time series. Is used by {@link #naiveCrossCorrelation(Data.Windowing.WindowMetadata)} to compute the partial results.
     * @param metadata describes the computation input. the two time series sets are expected to contain only a single element.
     * @return the cross correlation matrix between two time series.
     */
    protected static CorrelationMatrix naiveCrossCorrelationAtomic(WindowMetadata metadata){

        CorrelationMatrix result = new CorrelationMatrix(metadata);

        assert metadata.setA.size() == 1 && metadata.setB.size() == 1 : "The atomic version of the cross correlation matrix algorithm takes only two time series.";
        TimeSeries tsA = metadata.setA.get(0);
        TimeSeries tsB = metadata.setB.get(0);

        // will be used as placeholder for non-existing time series values (when windows extend outside the time series data)
        double placeHolderValue = Double.NaN;

        // will be filled the data of the current windows
        double[] windowAData = new double[metadata.windowSize],
                 windowBData = new double[metadata.windowSize];

        for (int i = 0; i < metadata.numBaseWindows; i++) {

            int baseWindowFrom = i * (metadata.windowSize - metadata.baseWindowOffset);

            double[] means = new double[metadata.getNumberOfDifferentTimeLags()];
            int lagIdx = 0;
            for(int lag : metadata.getDifferentTimeLags()){
                if(lag < 0)  {       // process negative time lags (shift time series A to the right ~ find influences of B on A)
                    getWindow(windowAData, tsB, baseWindowFrom, placeHolderValue);
                    getWindow(windowBData, tsA, baseWindowFrom+lag, placeHolderValue);
//                    System.out.println(String.format("negative lag. computing cc(tsB, tsA, from = %s, to = %s, lag = %s)", baseWindowFrom, baseWindowFrom+metadata.windowSize-1, lag));
                } else {             // process positive time lags (shift time series B to the right ~ find influences of A on B)
                    getWindow(windowAData, tsA, baseWindowFrom, placeHolderValue);
                    getWindow(windowBData, tsB, baseWindowFrom-lag, placeHolderValue);
//                    System.out.println(String.format("positive lag. computing cc(tsA, tsB, from = %s, to = %s, lag = %s)", baseWindowFrom, baseWindowFrom+metadata.windowSize-1, -lag));
                }

                means[lagIdx] = correlationCoefficient(windowAData, windowBData);

//                System.out.println(String.format("windowAData: %s", Arrays.toString(windowAData)));
//                System.out.println(String.format("windowBData: %s", Arrays.toString(windowBData)));
//                System.out.println(String.format("means[%s]: %s", lagIdx, means[lagIdx]));

                lagIdx++;

            }

            // create 0 values standard deviation array
            double[] stdDevs = new double[means.length];
            result.append(result.new CorrelationColumnBuilder(baseWindowFrom, metadata.tauMin).mean(means).standardDeviation(stdDevs).build());

            baseWindowFrom += metadata.baseWindowOffset;
        }

        return result;

    }

    /**
     * Naive windowed cross correlation algorithm.
     * @param metadata describes the computation input.
     * @return the windowed cross correlation between the two time series.
     */
    public static CorrelationMatrix naiveCrossCorrelation(WindowMetadata metadata){
        return CrossCorrelation.naiveCrossCorrelation(metadata, CrossCorrelation::naiveCrossCorrelationAtomic);
    }

    /**
     * Generic naive cross correlation matrix algorithm. Takes a function that computes the cross correlation matrix of two time series and aggregates the matrix
     * for larger input sets from the "atomic" cross correlation matrices between all combinations of two time series.
     * @param metadata describes the computation input.
     * @return the windowed cross correlation between the two time series.
     */
    protected static CorrelationMatrix naiveCrossCorrelation(WindowMetadata metadata, Function<WindowMetadata, CorrelationMatrix> crossCorrelationAtomic){

        final int numMatrices = metadata.setA.size() * metadata.setB.size();

        // compute one against one correlation matrices
        CorrelationMatrix[] partialResults = new CorrelationMatrix[numMatrices];
        int nextFreeSlot = 0;
        for(TimeSeries tsA : metadata.setA){
            for(TimeSeries tsB : metadata.setB){
                WindowMetadata partialMetadata = new WindowMetadata(tsA, tsB, metadata.windowSize, metadata.tauMin, metadata.tauMax, metadata.tauStep, metadata.baseWindowOffset);
                CorrelationMatrix.setSignificanceLevel(partialMetadata, CorrelationMatrix.getSignificanceLevel(metadata));
                partialResults[nextFreeSlot++] = crossCorrelationAtomic.apply(partialMetadata);
//                System.out.println(String.format("partialResults[nextFreeSlot-1]: %s", partialResults[nextFreeSlot - 1]));
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
                    if(Double.isNaN(r)) continue;
                    descriptiveStatistics.addValue(r);
                    if(tester.significanceTest(r)){
                        if(r < 0) numNegSig++;
                        else numPosSig++;
                    }
                }

                data[CorrelationMatrix.MEAN][lag] = descriptiveStatistics.getMean();
                data[CorrelationMatrix.STD_DEV][lag] = Math.sqrt(descriptiveStatistics.getPopulationVariance());

//                double[] percentiles = ApproximateMedian.getPercentiles(descriptiveStatistics.getValues(), 25, 50, 75);
                data[CorrelationMatrix.MEDIAN][lag] = descriptiveStatistics.getPercentile(50);
                data[CorrelationMatrix.IQR][lag] = descriptiveStatistics.getPercentile(75)-descriptiveStatistics.getPercentile(25); //percentiles[2]-percentiles[0];
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
        double normalizationTerm = Math.sqrt(summedSquaresX * summedSquaresY);

        return covariance / normalizationTerm;

    }

    public static double correlationCoefficient(double[] windowAData, double[] windowBData){

        // mean of first and second window
        double meanA = 0;
        for(double d : windowAData) meanA += d;
        meanA /= windowAData.length;
        double meanB = 0;
        for(double d : windowBData) meanB += d;
        meanB /= windowBData.length;

        // the sum of pointwise multiplied normalized measurements (enumerator term)
        double covariance = 0;
        // sum of squared normalized values of both windows
        double summedSquaresX = 0,
               summedSquaresY = 0;

        for (int i = 0; i < windowAData.length; i++) {

            double normalizedX = windowAData[i] - meanA;
            double normalizedY = windowBData[i] - meanB;

            covariance += normalizedX * normalizedY;

            summedSquaresX += normalizedX * normalizedX;
            summedSquaresY += normalizedY * normalizedY;
        }

        // the square root of the product of the rootOfSummedSquares (denominator term)
        double normalizationTerm = Math.sqrt(summedSquaresX * summedSquaresY);

        return covariance / normalizationTerm;

    }

    /**
     * Fills the given array with the time series values in the specified interval.
     * @param result allocated space where to store the values
     * @param ts time series
     * @param from offset of the first value (offset of the last one results from the length of the result array)
     * @param placeholder value to use if from or to extend outside the time series beginning or end
     */
    protected static void getWindow(double[] result, TimeSeries ts, int from, double placeholder){
        double[] data = ts.getDataItems().im;
        for (int i = 0; i < result.length; i++) {
            int sourceIndex = from + i;
            if(sourceIndex < 0 || sourceIndex >= data.length)
                result[i] = placeholder;
            else
                result[i] = data[sourceIndex];
        }
    }

    /**
     * Computes the average of a window of a time series. This method doesn't perform any index checking.
     * @param a time series
     * @param from start index of the window (inclusive). Must be a valid index in the time series' data array.
     * @param to last index of the window (inclusive). Must be a valid index in the time series' data array.
     * @return the sum of all values in the window, divided by the number of values
     */
    public static double mean(TimeSeries a, int from, int to){
        assert from <= to : String.format("Invalid window indices passed to mean computation. First window index %s needs to be smaller or equal to last window index %s.", from, to);

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

    public static enum NA_ACTION{
        //        NA_FAIL,
        REPLACE_WITH_ZERO,
        LEAVE_UNCHANGED
    }
}
