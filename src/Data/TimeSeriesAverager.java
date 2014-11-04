package Data;

import Data.Correlation.CrossCorrelation;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.util.*;

/**
 * Aggregates each time series of a time series set by forming groups of k consecutive data points and averaging them (the group value).
 * All time series are expected to have the same x values. Between each two x values, a 2D histogram is computed, summarizing the number of time series moving from
 *
 * TODO: histograms can be computed for the highest resolution (no binning on the x axis) and then aggregated easily to fit the current binning.
 * Laying out the histograms as 1D arrays could save some memory (factor of numBins less arrays/references). For 10k data points thats (4 byte per reference?) only 10k references instead of 160k references, saving of 600kb.
 * That wouldn't be worth it, but what about speed? Could it help (e.g. if caching loads the entire array instead of just parts of it?)
 */
public class TimeSeriesAverager {

    private final List<TimeSeries> timeSeries;

    /** The granularity of the aggregation. How many data points will be aggregated into one. */
    private final IntegerProperty groupSize = new SimpleIntegerProperty(20);

    public final void setGroupSize(int step){ groupSize.set(step); }
    final int getGroupSize(){ return groupSize.get(); }
    public final IntegerProperty groupSizeProperty(){ return groupSize; }

    /** The histogram resolution defines the number of bins in which the y value range between the minimum and the maximum at a data point index is divided. */
    public int numBins = 16;
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

    /** The minimum group value among all group values at a given group index. */
    public float minValues[];
    /** The maximum group value among all group values at a given group index. */
    public float maxValues[];

    /** Reusable auxiliary data structure for sorting the histogram bins. */
    List<Integer> indices = new ArrayList<>();

    /** For a data structure description, refer to the return parameter of {@link #compute(java.util.List, int)}. */
    private final Cacheable<double[][]> aggregatedData = new Cacheable<double[][]>() {

        /** Compare {@link Data.TimeSeriesAverager#numBins} parameter. */
        int cachedForNumBins = TimeSeriesAverager.this.numBins;
        /** The bin size (number of consecutive data points to be aggregated) for which the aggregation has been computed. */
        int cachedForBinSize = Integer.MIN_VALUE;
        /** Contains the ids of the time series which have been aggregated in ascending order. */
        int[] cachedForIds = new int[0];

        @Override public boolean isValid() {
            return getGroupSize() == cachedForBinSize
                    && numBins == cachedForNumBins
                    && cachedForIds.length == timeSeries.size()     // doesn't need to compare the arrays in depth if the sizes differ
                    && Arrays.equals(cachedForIds, timeSeries.stream().sorted().mapToInt(TimeSeries::getId).toArray());
        }

        @Override public void recompute() {
            int size = getGroupSize();
            set(TimeSeriesAverager.this.compute(timeSeries, size));
            cachedForIds = timeSeries.stream().sorted().mapToInt(TimeSeries::getId).toArray();
            cachedForBinSize = size;
            cachedForNumBins = numBins;
        }
    };


    public TimeSeriesAverager(List<TimeSeries> ensemble){ this.timeSeries = ensemble; }

    /**
     * @return The coarser time series in a compressed format.
     * The first dimension refers to time series idx, but 0 is for the x values and 1 ... N is for the y values of the respective time series.
     * The second dimension refers to data point index.
     * Returns an empty two-dimensional array if the time series list is empty.
     */
    double[][] compute(List<TimeSeries> timeSeries, int groupSize) {

        assert groupSize > 0 : "Aggregation group size must be at least one data point per group.";
        if(timeSeries.isEmpty()) return new double[0][0];

        TimeSeries anyTimeSeries = timeSeries.get(0);

        int numberOfDataPoints = (int) Math.ceil(1. * anyTimeSeries.getSize() / groupSize);
        double[][] newAggregatedData = new double[timeSeries.size()+1][numberOfDataPoints];

        minValues = new float[numberOfDataPoints];
        maxValues = new float[numberOfDataPoints];
        Arrays.fill(minValues, Float.POSITIVE_INFINITY);
        Arrays.fill(maxValues, Float.NEGATIVE_INFINITY);
        histograms = new short[numberOfDataPoints-1][numBins][numBins];
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
                    minValues[windowIdx] = Math.min(minValues[windowIdx], (float) mean);
                    maxValues[windowIdx] = Math.max(maxValues[windowIdx], (float) mean);
                }
                // proceed with the next window of this time series
                windowIdx++;
            }
        }

        // initialize index list for sorting
        if(indices.size() != numBins*numBins){
            indices = new ArrayList<>(numBins*numBins);
            for (int i = 0; i < numBins*numBins; i++) indices.add(i);
        }

        // calculate histograms of the binned data
        for (int histogramIdx = 0; histogramIdx < numberOfDataPoints-1; histogramIdx++) {
            float fromRange = maxValues[histogramIdx] - minValues[histogramIdx];
            float toRange = maxValues[histogramIdx+1] - minValues[histogramIdx+1];
            for (int tsIdx = 1; tsIdx <= timeSeries.size(); tsIdx++) {  // use one based time series index

                double fromY = newAggregatedData[tsIdx][histogramIdx];
                double toY = newAggregatedData[tsIdx][histogramIdx + 1];
                // if the time series value is NaN at one of the data points, the segment cannot contribute to the histogram in a sensible way.
                if(Double.isNaN(fromY) || Double.isNaN(toY)) continue;

                float relativeFrom = ((float) fromY - minValues[histogramIdx])/fromRange;
                float relativeTo = ((float) toY - minValues[histogramIdx+1])/toRange;

                // if the range is zero, a division by zero occurs, although putting all time series into the first bin is perfectly valid (since all bins cover the same single value)
                if(fromRange <= Float.MIN_VALUE) relativeFrom = 0;
                if(toRange <= Float.MIN_VALUE) relativeTo = 0;

                if( Float.isNaN(relativeFrom) || Float.isNaN(relativeTo) ) continue;

                int binFrom = (int) Math.floor(relativeFrom * numBins);
                if(binFrom == numBins) binFrom-=1;

                int binTo = (int) Math.floor(relativeTo * numBins);
                if(binTo == numBins) binTo-=1;

                assert histogramIdx >= 0 && binFrom >= 0 && binTo >= 0 : String.format("histogramIdx: %s binFrom: %s binTo: %s", histogramIdx, binFrom, binTo);
                histograms[histogramIdx][binFrom][binTo]++;
            }

            drawOrder[histogramIdx] = sortHistogram(histograms[histogramIdx]);
            maxBinValue[histogramIdx] = findHistogramMaxValue(histograms[histogramIdx]);
        }

        return newAggregatedData;

    }

    protected int[] sortHistogram(final short[][] histogram){
        // sort the histogram values and save the permutation
        Comparator<Integer> comparator = (i, j) -> {
            int rowI = i/numBins;
            int colI = i%numBins;
            int rowJ = j/numBins;
            int colJ = j%numBins;
            assert rowI < numBins && colI < numBins : String.format("rowI %s colI %s", rowI, colI);
            assert rowJ < numBins && colJ < numBins : String.format("rowJ %s colJ %s", rowJ, colJ);
            return Integer.compare(histogram[rowI][colI], histogram[rowJ][colJ]);
        };
        Collections.sort(indices, comparator);
        return indices.stream().mapToInt(value -> value).toArray();
    }



    /**
     * @param histogram a 2D histogram of size numBins x numBins containing non-negative values.
     * @return the maximum value over all bins of the histogram. */
    public short findHistogramMaxValue(short[][] histogram) {
        assert histogram.length == numBins && histogram[0].length == numBins : String.format("Histogram has invalid dimensions in peak detection routine. Should be %s x %s and is %s x %s",numBins,numBins,histogram.length,histogram[0].length);
        int maximum = 0;
        for (int i = 0; i < numBins; i++) {
            for (int j = 0; j < numBins; j++) {
                maximum = Math.max(Short.toUnsignedInt(histogram[i][j]), maximum);
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
}
