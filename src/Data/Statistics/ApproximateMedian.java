package Data.Statistics;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.Arrays;

/**
 * Finds the approximate median of an array of correlation values in linear time.
 * This is usually about 20 times faster than sorting the double array and
 * approximately 10 times faster than using the Apache Commons Math median finder.
 *
 * The trick is, since we know the correlation values are in range [-1, 1], we can use a counting sort.
 * By creating buckets for all numbers with the same k first decimals, instead of re-ordering the array,
 * counting the numbers that would fall in each bucket allows to quickly scan the cumulative distribution function
 * of the input data to find the median or other order statistics.
 *
 * The so found median deviates from the true median usually by at most ±1e-k where k is the {@link #decimals} used.
 *
 * USE WITH CARE! HASN'T BEEN THOROUGHLY TESTED YET!
 *
 * Created by Carl Witt on 08.06.14.
 */
public class ApproximateMedian {

    public ApproximateMedian(double[] data){
        /* the data to analyze */
        double[] data1 = data;
    }

    // slow
    public double sorting(double[] data){
        Arrays.sort(data);
        return data[data.length/2]; // integer division uses truncation to round (for positive numbers like the floor function)
    }

    // medium speed, giving an estimation of the population percentile based on the sample
    public double commons(double[] data){
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for(double d : data) stats.addValue(d);
        return stats.getPercentile(50);
    }

    private static final int decimals = 4; // only 10% slower than 3 decimals
    private static final int base = (int)Math.pow(10, decimals);
    private static final int[] bins = new int[2*base+1];

    // fast, approximate
    public static double[] getPercentiles(double[] data, int... percentiles){

        double[] result = new double[percentiles.length];

        // reseting the bins is faster than recreating them (reduces garbage collection overhead)
        // copying over a static array with zeros (using System.arraycopy) is suprisingly slower (on my machine?) and takes much memory anyway
        Arrays.fill(bins, 0);   // is at least as fast as a simple initializer loop

        // compute histogram
        for(double d : data){
            int idx = (int) ((d+1)*base);   // compute bin by truncating decimal places
            bins[idx]++;
        }

        // iterate over bins until half of the elements have been seen
        int elementsCovered = 0;
        int[] elementsToCover = new int[percentiles.length];
        for (int i = 0; i < percentiles.length; i++)
            elementsToCover[i] = Math.min(data.length, (int) Math.round(data.length * 100. / percentiles[i]));

//        System.out.println(String.format("elementsToCover: %s", Arrays.toString(elementsToCover)));
        int resultIdx = 0;
        for (int i = 0; i < bins.length; i++) {
            elementsCovered += bins[i];
            if(elementsCovered>=elementsToCover[resultIdx]){
                result[resultIdx] = ((double)i / base)-1;
                resultIdx++;
                if(resultIdx >= percentiles.length) return result;
            }
        }
        System.out.println(String.format("elementsToCover[resultIdx]: %s", elementsToCover[resultIdx]));
        throw new IllegalArgumentException("Sorting error.");
    }

}
