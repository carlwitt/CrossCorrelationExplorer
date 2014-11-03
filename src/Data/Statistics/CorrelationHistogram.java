package Data.Statistics;

import Data.Windowing.WindowMetadata;

/**
 * Stores aggregated information about a distribution of correlation values.
 * Created by Carl Witt on 23.10.14.
 */
public class CorrelationHistogram {

    public static final int NUM_BINS = 180;
    /** First dimension refers to rows, second dimension refers to columns, third dimension refers to bins.
     * The k-th int in the bin array represents the number of entries in the k-th bin.
     * The k-th bin represents the correlation value range [-1 + k * 2/numBins, -1 + (k+1) * 2/numBins) where the last interval is closed and not open. */
    public int[][][] bins;

    public CorrelationHistogram(WindowMetadata metadata){
        bins = new int[metadata.numBaseWindows][metadata.getNumberOfDifferentTimeLags()][];
    }

    public void setHistogramData(int row, int col, int[] histogramData){
        bins[row][col] = histogramData;
    }

    public int getFirstBin(int row, int col){
        return 0;
    }

    public int getLastBin(int row, int col){
        return NUM_BINS - 1;
    }

}
