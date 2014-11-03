package Data;

import Data.Correlation.CrossCorrelation;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;

import java.util.*;

/**
 * Aggregates each time series of a time series set by forming groups of k consecutive data points and averaging them.
 */
public class TimeSeriesAverager {

    private ObservableList<TimeSeries> timeSeries;

    /** The granularity of the aggregation. How many data points will be aggregated into one. */
    private final IntegerProperty binSize = new SimpleIntegerProperty(20);

    public final void setBinSize(int step){ binSize.set(step); }
    final int getBinSize(){ return binSize.get(); }
    public final IntegerProperty binSizeProperty(){ return binSize; }

    public int numBins = 16;
    /** dimension key [interval idx][fromBin][toBin] */
    public short[][][] histograms; // max value of an unsigned short is 2^16 ~ 64000 which should be enough time series per ensemble.
    /** dimension key [interval idx][order idx] */
    public int[][] drawOrder;

    public float minValues[];
    public float maxValues[];

    /** Refer to the return parameter of {@link #compute(java.util.List, int)} for a data structure description.
     */
    private Cacheable<double[][]> aggregatedData = new Cacheable<double[][]>() {

        int cachedForNumBins = TimeSeriesAverager.this.numBins;

        /** The bin size (number of consecutive data points to be aggregated) for which the aggregation has been computed. */
        int cachedForBinSize = Integer.MIN_VALUE;
        /** Contains the ids of the time series which have been aggregated in ascending order. */
        int[] cachedForIds = new int[0];

        @Override public boolean isValid() {
            return getBinSize() == cachedForBinSize
                    && numBins == cachedForNumBins
                    && cachedForIds.length == timeSeries.size()     // doesn't need to compare the arrays in depth if the sizes differ
                    && Arrays.equals(cachedForIds, timeSeries.stream().sorted().mapToInt(TimeSeries::getId).toArray());
        }

        @Override public void recompute() {
            int size = getBinSize();
            set(TimeSeriesAverager.this.compute(timeSeries, size));
            cachedForIds = timeSeries.stream().sorted().mapToInt(TimeSeries::getId).toArray();
            cachedForBinSize = size;
            cachedForNumBins = numBins;
        }
    };



    public TimeSeriesAverager(ObservableList<TimeSeries> ensemble){

        this.timeSeries = ensemble;

    }

    List<Integer> indices;

    /** Is triggered when time series are added or removed from the observed correlation set.
     * @return First dimension refers to time series idx, where 0 is for the x values and 1 ... N is for the y values of the respective time series.
     *  Second dimension refers to data point index.
     *  Returns an empty two-dimensional array if the time series list is empty.
     */
    protected double[][] compute(List<TimeSeries> timeSeries, int binSize) {

        assert binSize > 0 : "Bin size must be at least one data point per bin.";
        if(timeSeries.isEmpty()) return new double[0][0];

        TimeSeries anyTimeSeries = timeSeries.get(0);

        int numberOfDataPoints = (int) Math.ceil(1. * anyTimeSeries.getSize() / binSize);
        double[][] newAggregatedData = new double[timeSeries.size()+1][numberOfDataPoints];

        minValues = new float[numberOfDataPoints];
        maxValues = new float[numberOfDataPoints];
        Arrays.fill(minValues, Float.POSITIVE_INFINITY);
        Arrays.fill(maxValues, Float.NEGATIVE_INFINITY);
        histograms = new short[numberOfDataPoints-1][numBins][numBins];
        drawOrder = new int[histograms.length][];

        // sample x values
        int windowIdx = 0;
        for (int dataPointIdx = 0; dataPointIdx < anyTimeSeries.getSize(); dataPointIdx+=binSize) {
            newAggregatedData[0][windowIdx] = anyTimeSeries.getDataItems().re[dataPointIdx];
            windowIdx++;
        }

        // aggregate y values
        // for each time series
        for (int tsID = 0; tsID < timeSeries.size(); tsID++) {
            windowIdx = 0;
            // for each bin/window
            for (int windowStartIdx = 0; windowStartIdx < anyTimeSeries.getSize(); windowStartIdx += binSize) {
                // write the mean of the window as new y value
                double mean = CrossCorrelation.mean(timeSeries.get(tsID), windowStartIdx, Math.min(windowStartIdx + binSize - 1, anyTimeSeries.getSize() - 1));
                newAggregatedData[tsID+1][windowIdx] = mean;
                if(! Double.isNaN(mean)){
                    minValues[windowIdx] = Math.min(minValues[windowIdx], (float) mean);
                    maxValues[windowIdx] = Math.max(maxValues[windowIdx], (float) mean);
                }
                // proceed with the next window of this time series
                windowIdx++;
            }
        }

        indices = new ArrayList<>(numBins*numBins);
        for (int i = 0; i < numBins*numBins; i++) indices.add(i);

        // calculate histograms of the binned data
        for (int histogramIdx = 0; histogramIdx < numberOfDataPoints-1; histogramIdx++) {
            float fromRange = maxValues[histogramIdx] - minValues[histogramIdx];
            float toRange = maxValues[histogramIdx+1] - minValues[histogramIdx+1];
            for (int tsIdx = 1; tsIdx <= timeSeries.size(); tsIdx++) {  // use one based time series index
                float relativeFrom = ((float) newAggregatedData[tsIdx][histogramIdx] - minValues[histogramIdx])/fromRange;
                float relativeTo = ((float) newAggregatedData[tsIdx][histogramIdx+1] - minValues[histogramIdx+1])/toRange;
                if( Float.isNaN(relativeFrom) || Float.isNaN(relativeTo) ) continue;
                int binFrom = (int) Math.floor(relativeFrom * numBins);
                if(binFrom == numBins) binFrom-=1;
                int binTo = (int) Math.floor(relativeTo * numBins);
                if(binTo == numBins) binTo-=1;
                assert histogramIdx >= 0 && binFrom >= 0 && binTo >= 0 : String.format("histogramIdx: %s binFrom: %s binTo: %s", histogramIdx, binFrom, binTo);
                histograms[histogramIdx][binFrom][binTo]++;
            }

            drawOrder[histogramIdx] = sortHistogram(histograms[histogramIdx]);

        }

        return newAggregatedData;

    }

    protected int[] sortHistogram(final short[][] histogram){
        // sort the histogram values and save the permutation
        Comparator<Integer> comparator = new Comparator<Integer>() {
            public int compare(Integer i, Integer j) {
                int rowI = i/numBins;
                int colI = i%numBins;
                int rowJ = j/numBins;
                int colJ = j%numBins;
                assert rowI < numBins && colI < numBins : String.format("rowI %s colI %s", rowI, colI);
                assert rowJ < numBins && colJ < numBins : String.format("rowJ %s colJ %s", rowJ, colJ);
                return Integer.compare(histogram[rowI][colI], histogram[rowJ][colJ]);
            }
        };
        Collections.sort(indices, comparator);
        return indices.stream().mapToInt(value -> value).toArray();
    }



    /**
     * @param histogram a 2D histogram of size numBins x numBins containing non-negative values.
     * @return the maximum value over all bins of the histogram. */
    public int findHistogramMaxValue(short[][] histogram) {
        assert histogram.length == numBins && histogram[0].length == numBins : String.format("Histogram has invalid dimensions in peak detection routine. Should be %s x %s and is %s x %s",numBins,numBins,histogram.length,histogram[0].length);
        int maximum = 0;
        for (int i = 0; i < numBins; i++) {
            for (int j = 0; j < numBins; j++) {
                maximum = Math.max(histogram[i][j], maximum);
            }
        }
        return maximum;
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
