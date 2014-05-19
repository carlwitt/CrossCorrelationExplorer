package Data.Correlation;

import Data.TimeSeries;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CrossCorrelationTest {

    // test the slow but simple cross correlation implementation
    @Test
    public void testCrossCorrelationNaive(){

        int windowSize = 4, baseWindowOffset = 2, taumin = -1, tauMax = 2;
        TimeSeries tsA = new TimeSeries(new double[]{10,2,3,4,10,6,7,8,9});
        TimeSeries tsB = new TimeSeries(new double[]{3,0,6,4,2,1,5,-8,7});

        CorrelationMatrix exp = new CorrelationMatrix(null);
        CorrelationMatrix.Column[] expected = new CorrelationMatrix.Column[]{
                exp.new Column(new double[]{0.0834730012368303, -0.898026510133875, 0.679706623105669}, new double[3], 0, 0),
                exp.new Column(new double[]{0.291920179679905, -0.716270534268212, -0.53079104215763, 0.23083326514981}, new double[4], 2, -1),
                exp.new Column(new double[]{0.641426980589819, -0.104605206563161, 0.0366765706779718}, new double[3], 4, -1)
        };


        CorrelationMatrix result = CrossCorrelation.naiveCrossCorrelation(new CorrelationMetadata(tsA, tsB, windowSize, taumin, tauMax, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, baseWindowOffset));
        for (int i = 0; i < 3; i++) {
            assertArrayEquals(expected[i].mean, result.getItembyID(i).mean, 1e-15);
            assertEquals(expected[i].tauMin, result.getItembyID(i).tauMin);
            assertEquals(expected[i].windowStartIndex, result.getItembyID(i).windowStartIndex);
//            System.out.println(result.getItembyID(i).tauMin);
//            assertEquals(expected[i], result.getItembyID(i));
        }

    }

    // tests the naive correlation coefficient computation between two time windows.
    @Test
    public void testCorrelationCoefficientNaive() throws Exception {

        TimeSeries[] series = new TimeSeries[]{
                new TimeSeries(new double[]{1,2,3,4,5}),                // A
                new TimeSeries(new double[]{4,2,6,-3,11}),              // B1
                new TimeSeries(new double[]{100,101,102,103,104}),      // B2
                new TimeSeries(new double[]{-50,-51,-52,-53,-54})       // B3
        };


        double[] results = new double[]{
                CrossCorrelation.correlationCoefficient(series[0], series[1], 0, 4, 0), // A, B1
                CrossCorrelation.correlationCoefficient(series[0], series[2], 0, 4, 0), // A, B2
                CrossCorrelation.correlationCoefficient(series[0], series[3], 0, 4, 0), // A, B3
        };

        double[] expectedResults = new double[]{
                 0.276432802575278, // A, B1
                 1,                 // A, B2
                -1                  // A, B3
        };

        assertArrayEquals(expectedResults, results, 1e-14);

    }

    @Test @Ignore
    public void testCorrelationCoefficientPrecomputed() throws Exception {

        TimeSeries[] series = new TimeSeries[]{
                new TimeSeries(new double[]{1,2,3,4,5}),
                new TimeSeries(new double[]{4,2,6,-3,11}),
                new TimeSeries(new double[]{100,101,102,103,104}),
                new TimeSeries(new double[]{-50,-51,-52,-53,-54})
        };

        int windowSize = 5, tau = 0, delta = 1;
        LagWindowStatistics[] stats = new LagWindowStatistics[]{
                new LagWindowStatistics(series[0], windowSize, delta, tau, tau),
                new LagWindowStatistics(series[1], windowSize, delta, tau, tau),
                new LagWindowStatistics(series[2], windowSize, delta, tau, tau),
                new LagWindowStatistics(series[3], windowSize, delta, tau, tau),
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

    // Simple calculation of the mean of time series values in a window
    @Test
    public void testMean() throws Exception {

        assertEquals(3, CrossCorrelation.mean(new TimeSeries(new double[]{1, 2, 3, 4, 5}), 0, 4), 1e-15);
        assertEquals(1, CrossCorrelation.mean(new TimeSeries(new double[]{1,1,1,1,1}), 0, 4), 1e-15);
        assertEquals(0, CrossCorrelation.mean(new TimeSeries(new double[]{0}), 0, 0), 1e-15);

    }

    // Test the computation of the mean of a window from a previous window
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

    /**
     * Generate two sine curves, one without and one with a time lag.
     * Writes the data as text file.
     */
    @Test
    public void generateSineTestData(){

        int resolution = 1000;

        // one is computed

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