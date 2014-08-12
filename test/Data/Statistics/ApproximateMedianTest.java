package Data.Statistics;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.junit.Test;

import java.util.Random;

public class ApproximateMedianTest {

    @Test public void negativePercentiles(){

        double[] data = new double[]{
                -0.012086732064244697,
                -0.24975668704012527,
                0.5706168483164684,
                -0.322111769955327,
                0.24166759508327315,
                Double.NaN,
                0.16698443218942854,
                -0.10427763937565114,
                -0.15595963093172435,
                -0.028075857595882995,
                -0.24137994506058857,
                0.47543170476574426,
                -0.07495595384947631,
                0.37445697625436497,
                -0.09944199541668033
        };
        DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics(data);

        double threeQuarters = descriptiveStatistics.getPercentile(75);
        double oneQuarter = descriptiveStatistics.getPercentile(25);

        double IQR = threeQuarters - oneQuarter;

        System.out.println(String.format("25th percentile %s 75th percentile %s", oneQuarter, threeQuarters ));

        assert IQR >= 0;

    }
    @Test public void medianSpeed(){

        int inputSets = 500;
        int setSize = 100001;
        double[][] data = new double[inputSets][setSize];   // one row per input set

        int COMMONS = 0, COUNTING = 1, STD_SORT = 2;
        double[][] results = new double[inputSets][3];      // one row per result, one column per method

        ApproximateMedian medianFinder = new ApproximateMedian(null);

        // generate random input data
        Random random = new Random(3l);

//        RandomDataGenerator rdg = new RandomDataGenerator();

        for (double[] set : data){
            for (int i = 0; i < setSize; i++) set[i] = 2*random.nextDouble()-1;
        }

        // check commons math
        long before = System.currentTimeMillis();
        for (int i = 0; i < data.length; i++)
            results[i][COMMONS] = medianFinder.commons(data[i]);
        System.out.println("Commons: " + (System.currentTimeMillis()-before));

        // check counting sort
        before = System.currentTimeMillis();
        for (int i = 0; i < data.length; i++)
            results[i][COUNTING] = ApproximateMedian.getPercentiles(data[i], 50)[0];
        System.out.println("Counting: " + (System.currentTimeMillis()-before));

        // check standard sort (slow)
//        before = System.currentTimeMillis();
//        for (int i = 0; i < data.length; i++)
//            results[i][STD_SORT] = medianFinder.sorting(data[i]);
//        System.out.println("Std sort: " + (System.currentTimeMillis()-before));

//        System.out.println(String.format("%s\t%s\t%s","Commons", "Counting", "Std sort"));
        DescriptiveStatistics errorStats = new DescriptiveStatistics();
        for (int i = 0; i < results.length; i++)
            errorStats.addValue(results[i][COUNTING] - results[i][COMMONS]);

        System.out.println(String.format("Errors produced by counting sort:\nmin: %s\nmax: %s\nsdv: %s",errorStats.getMin(),errorStats.getMax(),errorStats.getStandardDeviation()));
//            System.out.println(String.format("%s\t%s\t%s", results[i][COMMONS], results[i][COUNTING], results[i][STD_SORT]));


    }

}