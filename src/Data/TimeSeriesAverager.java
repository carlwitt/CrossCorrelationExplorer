package Data;

import Data.Correlation.CrossCorrelation;
import Global.Util;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.util.*;

/**
 * Aggregates each time series of a time series set by forming groups of k consecutive data points and averaging them (the group value).
 * All time series are expected to have the same x values. Between each two x values, a 2D histogram is computed, summarizing the number of time series moving from
 *
 * TODO: histograms can be computed for the highest resolution (no binning on the x axis) and then aggregated easily to fit the current binning.
 * Laying out the histograms as 1D arrays could save some memory (factor of numBins less arrays/references). For 10k data points that's (4 byte per reference?) only 10k references instead of 160k references, a saving of 600kb.
 * That wouldn't be worth it, but what about speed? Could it help (e.g. if caching loads the entire array instead of just parts of it?)
 */
public class TimeSeriesAverager {

    private final List<TimeSeries> timeSeries;

    /** The granularity of the aggregation. How many data points will be aggregated into one. */
    private final IntegerProperty groupSize = new SimpleIntegerProperty(20);
    public final void setGroupSize(int step){ groupSize.set(step); }
    public final int getGroupSize(){ return groupSize.get(); }
    public final IntegerProperty groupSizeProperty(){ return groupSize; }

    /** The bin size defines the range (in data coordinates) that a single bin covers (on the y axis), e.g. 0.1 ˚C. */
    public double binSize = 0.05;

    /**
     * Contains the "slope" distribution between two data point indices.
     * The first dimension refers to the interval index,
     * the second to the bin index where the time series segments starts and
     * the third dimension to the bin index where the time series segment ends.
     * Note that each bin can record at most 2^16 ~ 64000 time series due to the SHORT data type restrictions.
     */
    public short[][][] histograms;
    /** The maximum frequency occurring in any bin of the histogram with the given index. */
    public short[] maxBinValue;
    /** Contains the indices of the bins in non-increasing order of their time series counts.
     * The first dimension refers to the group index,
     * the second dimension refers to the order index and contains the bin row and column indices encoded as row * numBins + col * numBins. */
    public int[][] drawOrder;

    /** For each histogram, contains the lower bound of the lowest bin. In addition, the last element of the array contains the lower bound of the binning of the last time step (for which there is no histogram).
     * Given that one bin starts at 0, lowestBinStartsAt is the bin lower bound that the minimum y value lies in. */
    public double[] lowestBinStartsAt;

    /** The minimum group value among all group values at a given group index. */
    public double minValues[];
    /** The maximum group value among all group values at a given group index. */
    public double maxValues[];

    /** Reusable auxiliary data structure for sorting the histogram bins. */
    List<Integer> indices = new ArrayList<>();

    /** For a data structure description, refer to the return parameter of {@link #compute(java.util.List, int, double)}. */
    private final Cacheable<double[][]> aggregatedData = new Cacheable<double[][]>() {

        /** Compare {@link Data.TimeSeriesAverager#binSize} parameter. */
        double cachedForBinSize = TimeSeriesAverager.this.binSize;
        /** The bin size (number of consecutive data points to be aggregated) for which the aggregation has been computed. */
        int cachedForGroupSize = Integer.MIN_VALUE;
        /** Contains the ids of the time series which have been aggregated in ascending order. */
        int[] cachedForIds = new int[0];

        @Override public boolean isValid() {
            return getGroupSize() == cachedForGroupSize
                    && Math.abs(binSize - cachedForBinSize) <= 2*Double.MIN_VALUE
                    && Arrays.equals(cachedForIds, timeSeries.stream().sorted().mapToInt(TimeSeries::getId).toArray());
        }

        @Override public void recompute() {
            int size = getGroupSize();
            set(TimeSeriesAverager.this.compute(timeSeries, size, binSize));
            cachedForIds = timeSeries.stream().sorted().mapToInt(TimeSeries::getId).toArray();
            cachedForGroupSize = size;
            cachedForBinSize = binSize;
        }
    };


    public TimeSeriesAverager(List<TimeSeries> ensemble){
        // check whether the unsigned short data type is large enough to store the largest possible frequency in a histogram summarizing a time step in an ensemble of the given size.
        if(ensemble.size() > 2*Short.MAX_VALUE+1) throw new IllegalArgumentException("Ensemble is too large.");
        this.timeSeries = ensemble;
    }

    /**
     * @return The coarser time series in a compressed format.
     * The first dimension refers to time series idx, but 0 is for the x values and 1 ... N is for the y values of the respective time series.
     * The second dimension refers to data point index.
     * Returns an empty two-dimensional array if the time series list is empty.
     */
    double[][] compute(List<TimeSeries> timeSeries, int groupSize, double binSize) {

        assert groupSize > 0 : "Aggregation group size must be at least one data point per group.";
        if(timeSeries.isEmpty()) return new double[0][0];

        Util.TimeOutChecker timeOutChecker = new Util.TimeOutChecker(10000); // 10 sec time out for aggregation

        TimeSeries anyTimeSeries = timeSeries.get(0);

        int numberOfDataPoints = (int) Math.ceil(1. * anyTimeSeries.getSize() / groupSize);
        double[][] newAggregatedData = new double[timeSeries.size()+1][numberOfDataPoints];

        minValues = new double[numberOfDataPoints];
        maxValues = new double[numberOfDataPoints];
        Arrays.fill(minValues, Double.POSITIVE_INFINITY);
        Arrays.fill(maxValues, Double.NEGATIVE_INFINITY);
        histograms = new short[numberOfDataPoints-1][][];
        lowestBinStartsAt = new double[numberOfDataPoints];
        maxBinValue = new short[numberOfDataPoints-1];
        drawOrder = new int[histograms.length][];

        // sample x values
        int windowIdx = 0;
        for (int dataPointIdx = 0; dataPointIdx < anyTimeSeries.getSize(); dataPointIdx+=groupSize) {
            newAggregatedData[0][windowIdx] = anyTimeSeries.getDataItems().re[dataPointIdx];
            windowIdx++;
        }

        // aggregate y values
        // for each time series
        for (int tsID = 0; tsID < timeSeries.size(); tsID++) {
            windowIdx = 0;
            // for each group/window
            for (int windowStartIdx = 0; windowStartIdx < anyTimeSeries.getSize(); windowStartIdx += groupSize) {
                // write the mean of the window as new y value
                double mean = CrossCorrelation.mean(timeSeries.get(tsID), windowStartIdx, Math.min(windowStartIdx + groupSize - 1, anyTimeSeries.getSize() - 1));
                newAggregatedData[tsID+1][windowIdx] = mean;
                if(! Double.isNaN(mean)){
                    minValues[windowIdx] = Math.min(minValues[windowIdx], mean);
                    maxValues[windowIdx] = Math.max(maxValues[windowIdx], mean);
                }
                // proceed with the next window of this time series
                windowIdx++;
            }
        }

        // calculate histograms of the binned data
        // each bin has the same size. The k-th bin covers values in range [k * binSize, (k+1) * binSize[
        for (int histogramIdx = 0; histogramIdx < numberOfDataPoints-1; histogramIdx++) {

            // compute the number of source bins for the first histogram
            int lowestSourceBinIdx = (int) Math.floor(minValues[histogramIdx] / binSize);
            int highestSourceBinIdx = (int) Math.floor(maxValues[histogramIdx] / binSize);
            int numSourceBins = Math.max(1, highestSourceBinIdx - lowestSourceBinIdx + 1); // if the min value is exactly the max value and exactly on a bin bound, allocate a bin anyway
            lowestBinStartsAt[histogramIdx] = lowestSourceBinIdx * binSize;

            // compute the number of sink bins
            int lowestSinkBinIdx = (int) Math.floor(minValues[histogramIdx + 1] / binSize);
            int highestSinkBinIdx = (int) Math.floor(maxValues[histogramIdx + 1] / binSize);
            int numSinkBins = Math.max(1, highestSinkBinIdx - lowestSinkBinIdx + 1); // if the min value is exactly the max value and exactly on a bin bound, allocate a bin anyway

            histograms[histogramIdx] = new short[numSourceBins][numSinkBins];

            for (int tsIdx = 1; tsIdx <= timeSeries.size(); tsIdx++) {  // use one based time series index (because 0 is reserved for the x values)

                double sourceY = newAggregatedData[tsIdx][histogramIdx];
                double sinkY = newAggregatedData[tsIdx][histogramIdx + 1];

                // if the time series value is NaN at one of the data points, the segment cannot contribute to the histogram in a sensible way.
                if(Double.isNaN(sourceY) || Double.isNaN(sinkY)) continue;

                int sourceBinIdx = (int) Math.floor(sourceY / binSize);
                int sinkBinIdx = (int) Math.floor(sinkY / binSize);

                // the source and sink bin indices can not become too large, because of the half-open definition of the bins

                assert sourceBinIdx >= lowestSourceBinIdx && sinkBinIdx >= lowestSinkBinIdx
                        && sourceBinIdx-lowestSourceBinIdx < histograms[histogramIdx].length
                        && sinkBinIdx-lowestSinkBinIdx < histograms[histogramIdx][0].length :
                        String.format(  "\nsourceY: %s source bin idx: %s (must be ≥ %s)" +
                                        "\nsinkY: %s sink bin idx: %s (must be ≥ %s)" +
                                        "\nnormalized sourceBinIdx: %s must be in range [0, %s] " +
                                        "\nnormalized sinkBinIdx: %s must be in range [0, %s]" +
                                        "\nhistogram idx: %s" +
                                        "\nsource bin min max = (%s, %s)" +
                                        "\nsink bin min max = (%s, %s)" +
                                        "\n bin size %s",
                                sourceY, sourceBinIdx, lowestSourceBinIdx,
                                sinkY, sinkBinIdx, lowestSinkBinIdx,
                            sourceBinIdx-lowestSourceBinIdx, histograms[histogramIdx].length,
                            sinkBinIdx-lowestSinkBinIdx,     histograms[histogramIdx][0].length,
                            histogramIdx,
                            minValues[histogramIdx], maxValues[histogramIdx],
                            minValues[histogramIdx+1], maxValues[histogramIdx+1],
                            binSize);

                histograms[histogramIdx][sourceBinIdx - lowestSourceBinIdx][sinkBinIdx - lowestSinkBinIdx]++;
            }

//            lowestSourceBinIdx = lowestSinkBinIdx; // would be nicer to cache
            drawOrder[histogramIdx] = sortHistogram(histograms[histogramIdx]);
            maxBinValue[histogramIdx] = findHistogramMaxValue(histograms[histogramIdx]);

            if(timeOutChecker.isTimeOutUserWantsToAbort()) return aggregatedData.cachedValue;

        }

        // compute the lowest bin bound for the last data point index
        int lowestSourceBinIdx = (int) Math.floor(minValues[numberOfDataPoints-2] / binSize);
        lowestBinStartsAt[numberOfDataPoints-1] = lowestSourceBinIdx * binSize;

        return newAggregatedData;

    }

    protected int[] sortHistogram(final short[][] histogram){

        int numSourceBins = histogram.length;
        int numSinkBins = histogram[0].length;

        // initialize index list for sorting
        if(indices.size() != numSourceBins * numSinkBins){
            indices.clear();
            for (int i = 0; i < numSourceBins*numSinkBins; i++) indices.add(i);
        }

        // sort the histogram values and save the permutation
        Comparator<Integer> comparator = (i, j) -> {
            int rowI = i/numSinkBins;
            int colI = i%numSinkBins;
            int rowJ = j/numSinkBins;
            int colJ = j%numSinkBins;
            assert rowI < numSourceBins && colI < numSinkBins : String.format("rowI %s colI %s numSourceBins: %s numSinkBins: %s", rowI, colI, numSourceBins, numSinkBins);
            assert rowJ < numSourceBins && colJ < numSinkBins : String.format("rowJ %s colJ %s", rowJ, colJ);
            return Integer.compare(histogram[rowI][colI], histogram[rowJ][colJ]);
        };
        Collections.sort(indices, comparator);
        return indices.stream().mapToInt(value -> value).toArray();
    }



    /**
     * @param histogram a 2D histogram of size numBins x numBins containing non-negative values.
     * @return the maximum value over all bins of the histogram. */
    public short findHistogramMaxValue(short[][] histogram) {
        assert histogram.length > 0 : "Illegal histogram dimensions. Must have at least one source bin.";
        int maximum = 0;
        for (short[] toSinkBin : histogram) {
            for (int j = 0; j < histogram[0].length; j++) {
                maximum = Math.max(Short.toUnsignedInt(toSinkBin[j]), maximum);
            }
        }
        return (short) maximum; // don't worry, the cast is correctly undone by using toUnsignedInt()
    }

    /** Returns the x values of the aggregated data points. */
    public double[] getXValues(){
        return aggregatedData.get()[0];
    }

    /**
     * @param oneBasedTimeSeriesIdx One based (!) time series index.
     * @return the y values of the aggregated time series with the given ID. */
    public double[] getYValues(int oneBasedTimeSeriesIdx){
        return aggregatedData.get()[oneBasedTimeSeriesIdx];
    }

    public int getNumberOfTimeSeries() { return aggregatedData.get().length-1; }

    public void setBinSize(double binSize) {
        this.binSize = binSize;
    }

    public double getBinSize() {
        return binSize;
    }
}
