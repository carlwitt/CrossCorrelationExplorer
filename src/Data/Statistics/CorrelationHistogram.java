package Data.Statistics;

import Data.Windowing.WindowMetadata;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.Arrays;

/**
 * Stores aggregated information about a distribution of correlation values (i.e. in range [-1, 1] within each cell of a column.
 * Created by Carl Witt on 23.10.14.
 */
public class CorrelationHistogram {

    public static final int NUM_BINS = 180;
    /** The smallest divisors of 180. Can be used to derive lesser resolution histograms from the histogram. */
    public static final int[] divisors = new int[]{1,2,3,4,5,6,9,10,12,15,18};

    /** First dimension refers to row idx (time lag idx), second dimension refers to bins.
     * The k-th short in the inner array represents the number of entries in the k-th bin.
     * The k-th bin represents the correlation value range [-1 + k * 2/numBins, -1 + (k+1) * 2/numBins) where the last interval is closed and not open. */
    private short[][] frequencies;

    // -----------------------------------------------------------------------------------------------------------------
    // numeric compression scheme
    // -----------------------------------------------------------------------------------------------------------------

    /** The maximum frequency that can occur in any bin. */
    public static final int MAX_SUPPORTED_FREQUENCY = 4000000;
    /** The largest value that an unsigned short variable can take. */
    private static final int UNSIGNED_SHORT_MAX_VALUE = Short.MAX_VALUE * 2 + 1;
    /** Defines the switching point between linear (lossless) and logarithmic (lossy) mapping from integers to shorts. */
    private static final int BREAK_POINT = Short.MAX_VALUE;

    /** Temporary memory reserved for creating new histograms. Avoids repeated allocation of short lived arrays that have to be garbage collected. */
    private int[] intermediateHistogram = new int[NUM_BINS];

    /** Used to map an integer in range [0, {@link #MAX_SUPPORTED_FREQUENCY}] into the value range of an unsigned short. */
    private double forwardBase = Math.log(MAX_SUPPORTED_FREQUENCY + 1) / UNSIGNED_SHORT_MAX_VALUE;
    /** Used to map a short into the value range [0, {@link #MAX_SUPPORTED_FREQUENCY}]. */
    private double reverseBase = Math.exp(forwardBase);

    // -----------------------------------------------------------------------------------------------------------------
    // methods
    // -----------------------------------------------------------------------------------------------------------------

    public CorrelationHistogram(WindowMetadata metadata){
        assert metadata.setA.size() * metadata.setB.size() <= MAX_SUPPORTED_FREQUENCY : "One or both ensembles are too large for being processed!";
        frequencies = new short[metadata.getNumberOfDifferentTimeLags()][];
    }

    /**
     * Computes a histogram with fixed domain [-1, 1] and variable number of bins.
     * @param stats The distribution for which to approximate the density.
     * @param numBins The number of bins that will divide the [-1, 1] range.
     * @return The k-th int represents the number of entries (frequency) in the k-th bin.
     * The k-th bin represents the correlation value range [-1 + k * 2/numBins, -1 + (k+1) * 2/numBins) where the last interval is closed and not open. */
    public static int[] computeHistogram(DescriptiveStatistics stats, int numBins) {

        double min = -1; //stats.getMin();
        double max =  1; //stats.getMax();
        final double binSize = (max - min)/numBins;

        final int[] binCounts = new int[numBins];
        double[] values = stats.getValues();
        for (double d : values) {
            int bin = (int) ((d - min) / binSize);
            assert bin >= 0 && bin <= numBins : String.format("Invalid bin idx: %s not in [%s, %s] value %s (min %s)", bin, 0, numBins, d, min);
            binCounts[bin == numBins ? numBins - 1 : bin] += 1;
        }
        return binCounts;
    }

    /** Computes the histogram for the given values and associates it with a given row (of the column) index. */
    public void setDistribution(int row, double[] distribution){
        frequencies[row] = computeHistogram(distribution);
    }

    /**
     * Gives the histogram of the correlation distribution for a given cell (row within this column).
     * @param row The row (or time lag) index
     * @param histogram An array of length {@link #NUM_BINS} to store the data to
     */
    public void getHistogram(int row, int[] histogram){
        decompressHistogram(frequencies[row], histogram);
    }

    private short[] computeHistogram(double[] distribution) {

        // reset intermediate memory
        Arrays.fill(intermediateHistogram, 0);

        for (int i = 0; i < distribution.length; i++) {
            int binIdx = (int) ((distribution[i] + 1.) / 2. * NUM_BINS);
            intermediateHistogram[binIdx < NUM_BINS ? binIdx : NUM_BINS]++;
        }
        return compressHistogram(intermediateHistogram);
    }

    private short[] compressHistogram(int[] histogram) {
        short[] compressed = new short[NUM_BINS];
        for (int i = 0; i < histogram.length; i++) {
            int frequency = histogram[i];
            // take the logarithm of the frequency plus one to forward base, giving a value in range [0, UNSIGNED_SHORT_MAX_VALUE]
            double mapped = frequency < BREAK_POINT ? frequency : Math.ceil(Math.log(frequency + 1) / forwardBase);
            compressed[i] = (short) mapped;
        }
        return compressed;
    }

    private void decompressHistogram(short[] compressedHistogram, int[] decompressedHistogram) {
        for (int i = 0; i < compressedHistogram.length; i++) {
            short transformed = compressedHistogram[i];
            int inverseTransformed = transformed >= 0 && transformed < BREAK_POINT ? Short.toUnsignedInt(transformed) :
                    (int) Math.pow(reverseBase, Short.toUnsignedInt(transformed))-1;
            decompressedHistogram[i] = inverseTransformed;
        }
    }

    public int[] getHistogram(int row) {
        int[] histogram = new int[NUM_BINS];
        decompressHistogram(frequencies[row], histogram);
        return histogram;
    }
}
