package Data.Statistics;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.Arrays;

/**
 * Finds the approximate median of a double array in linear time.
 * This is usually about 20 times faster than sorting the double array and
 * approximately 10 times faster than using the Apache Commons Math median finder.
 * The trick is to use a counting sort. By creating buckets for all numbers with the same k first decimals,
 * instead of re-ordering the array, counting the numbers that would fall in each bucket
 * allows to quickly scan the cumulative distribution function of the input data and to find the median.
 *
 * The so found median deviates from the true median usually by at most ±1e-k where k is the {@link #decimals} used.
 *
 * Created by Carl Witt on 08.06.14.
 */
public class ApproximateMedian {

    /** the data to analyze */
    double[] data;
    public ApproximateMedian(double[] data){
        this.data = data;
    }

    // slow
    public double sorting(double[] data){
        Arrays.sort(data);
        return data[data.length/2]; // integer division uses truncation to round (for positive numbers like the floor function)
    }

    // ok, precise
    public double commons(double[] data){
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for(double d : data) stats.addValue(d);
        return stats.getPercentile(50);
    }

    int decimals = 4; // only 10% slower than 3 decimals
    int base = (int)Math.pow(10,decimals);
    int[] bins = new int[2*base];

    // fast, approximate
    public double countingSort(double[] data){

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
        int elementsToCover = data.length/2;
        for (int i = 0; i < bins.length; i++) {
            elementsCovered += bins[i];
            if(elementsCovered>=elementsToCover) return ((double)i / base)-1;
        }
        throw new IllegalArgumentException("Sorting error.");
    }

}
