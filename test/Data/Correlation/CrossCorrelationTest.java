package Data.Correlation;

import Data.TimeSeries;
import org.junit.Test;

import static org.junit.Assert.*;

public class CrossCorrelationTest {

    @Test public void testCorrelationCoefficientNaive() throws Exception {

        TimeSeries[] series = new TimeSeries[]{
                new TimeSeries(new double[]{1,2,3,4,5}),
                new TimeSeries(new double[]{4,2,6,-3,11}),
                new TimeSeries(new double[]{100,101,102,103,104}),
                new TimeSeries(new double[]{-50,-51,-52,-53,-54})
        };


        double[] results = new double[]{
                CrossCorrelation.correlationCoefficient(series[0], series[1], 0, 4, 0),
                CrossCorrelation.correlationCoefficient(series[0], series[2], 0, 4, 0),
                CrossCorrelation.correlationCoefficient(series[0], series[3], 0, 4, 0),
        };

        double[] expectedResults = new double[]{
                 0.276432802575278,
                 1,
                -1
        };

        assertArrayEquals(expectedResults, results, 1e-14);

    }

    @Test public void testCorrelationCoefficientPrecomputed() throws Exception {

        TimeSeries[] series = new TimeSeries[]{
                new TimeSeries(new double[]{1,2,3,4,5}),
                new TimeSeries(new double[]{4,2,6,-3,11}),
                new TimeSeries(new double[]{100,101,102,103,104}),
                new TimeSeries(new double[]{-50,-51,-52,-53,-54})
        };

        int windowSize = 5, tau = 0, delta = 1;
        StatisticsForWindows[] stats = new StatisticsForWindows[]{
                new StatisticsForWindows(series[0], windowSize, delta, tau, tau, true),
                new StatisticsForWindows(series[1], windowSize, delta, tau, tau, false),
                new StatisticsForWindows(series[2], windowSize, delta, tau, tau, false),
                new StatisticsForWindows(series[3], windowSize, delta, tau, tau, false),
        };


        double[] results = new double[]{
                CrossCorrelation.correlationCoefficient(stats[0], stats[1], 0, tau),
                CrossCorrelation.correlationCoefficient(stats[0], stats[2], 0, tau),
                CrossCorrelation.correlationCoefficient(stats[0], stats[3], 0, tau),
        };

        double[] expectedResults = new double[]{
                0.276432802575278,
                1,
                -1
        };

        assertArrayEquals(expectedResults, results, 1e-14);

    }

    @Test
    public void testMean() throws Exception {

        assertEquals(3, CrossCorrelation.mean(new TimeSeries(new double[]{1, 2, 3, 4, 5}), 0, 4), 1e-15);
        assertEquals(1, CrossCorrelation.mean(new TimeSeries(new double[]{1,1,1,1,1}), 0, 4), 1e-15);
        assertEquals(0, CrossCorrelation.mean(new TimeSeries(new double[]{0}), 0, 0), 1e-15);

    }

    @Test
    public void testIncrementalMean() throws Exception {

        TimeSeries a = new TimeSeries(new double[]{1,2,3,4,5,6,7,8});

        double initialMean = CrossCorrelation.mean(a, 0, 2); // 2

        double incrementalMean = CrossCorrelation.incrementalMean(a, 1, 3, initialMean, 0);
        assertEquals(3, incrementalMean, 1e-15);

        incrementalMean = CrossCorrelation.incrementalMean(a, 2, 4, incrementalMean, 1);
        assertEquals(4, incrementalMean, 1e-15);

        incrementalMean = CrossCorrelation.incrementalMean(a, 3, 5, incrementalMean, 2);
        assertEquals(5, incrementalMean, 1e-15);

    }

//    // The incremental mean algorithm performs very well: speedups seem to converge to 0.1 * |w| (where |w\ denotes the window size)
//    // for overlaps converging to 100%. to The compiler doesn't seem to be able to optimize this on its own.
//    @Test
//    public void testIncrementalMeanSpeed() throws Exception {
//
//        int n = 10000;
//        int windowSize = 100;
//        int overlapStep = 1;
//
//        double[] data = new double[n];
//        for (int i = 0; i < n; i++)
//            data[i] = 200*Math.random()-100;
//
//        TimeSeries a = new TimeSeries(data);
//
//        for (int overlap = 4*windowSize/5; overlap <= windowSize-1; overlap+=overlapStep) {
//
//            long before = System.currentTimeMillis();
//            for (int i = 0; i < 10000; i++) {
//                int from = windowSize - overlap;
//                double incrementalMean = CrossCorrelation.mean(a, 0, windowSize-1);
//                while(from + windowSize-1 < n){
//                    incrementalMean =  CrossCorrelation.incrementalMean(a, from, from+windowSize-1, incrementalMean, from-(windowSize-overlap));
////                assertEquals(incrementalMean, CrossCorrelation.mean(a,from,from+windowSize-1), 1e-15);
//                    from+=windowSize-overlap;
//                }
//            }
//            long incrementalMs = System.currentTimeMillis()-before;
//
//            before = System.currentTimeMillis();
//
//            for (int i = 0; i < 10000; i++) {
//                int from = 0;
//                while(from + windowSize-1 < n){
//                    CrossCorrelation.mean(a,from,from+windowSize-1);
//                    from+=windowSize-overlap;
//                }
//            }
//
//            long bruteForceMs = System.currentTimeMillis()-before;
//
//            System.out.println(String.format("overlap %s%% brute %s incremental %s speedup %s", 1.*overlap/windowSize*100, bruteForceMs, incrementalMs, 1.*bruteForceMs/incrementalMs));
//
//
//        }
//
//    }
}