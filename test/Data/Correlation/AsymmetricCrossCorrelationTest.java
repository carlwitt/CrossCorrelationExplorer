package Data.Correlation;

import Data.IO.FileModel;
import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import org.apache.commons.math3.analysis.function.Atanh;
import org.apache.commons.math3.distribution.TDistribution;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class AsymmetricCrossCorrelationTest {

    //------------------------------------------------------------------------------------------------------------------
    // cross correlation
    //------------------------------------------------------------------------------------------------------------------

    // test the slow but simple cross correlation implementation
    @Test
    public void testCrossCorrelationNaive(){

        int windowSize = 4, baseWindowOffset = 2, taumin = -1, tauMax = 2;
        TimeSeries tsA = new TimeSeries(1, 10,2,3,4,10,6,7,8,9);
        TimeSeries tsB = new TimeSeries(1, 3,0,6,4,2,1,5,-8,7);

        CorrelationMatrix exp = new CorrelationMatrix(null);
        CorrelationMatrix.CorrelationColumn[] expected = new CorrelationMatrix.CorrelationColumn[]{
                exp.new CorrelationColumnBuilder(0, 0).mean(new double[]{0.0834730012368303, -0.898026510133875, 0.679706623105669}).standardDeviation(new double[3]).build(),
                exp.new CorrelationColumnBuilder(2, -1).mean(new double[]{0.291920179679905, -0.716270534268212, -0.53079104215763, 0.23083326514981}).standardDeviation(new double[4]).build(),
                exp.new CorrelationColumnBuilder(4, -1).mean(new double[]{0.641426980589819, -0.104605206563161, 0.0366765706779718}).standardDeviation(new double[3]).build()
        };


        CorrelationMatrix result = AsymmetricCrossCorrelation.naiveCrossCorrelation(CorrelationMatrix.setSignificanceLevel(new WindowMetadata(tsA, tsB, windowSize, taumin, tauMax, 1, baseWindowOffset), 0.05));
        for (int i = 0; i < 3; i++) {
            assertArrayEquals(expected[i].data[CorrelationMatrix.MEAN], result.getColumn(i).data[CorrelationMatrix.MEAN], 1e-15);
            assertEquals(expected[i].tauMin, result.getColumn(i).tauMin);
            assertEquals(expected[i].windowStartIndex, result.getColumn(i).windowStartIndex);
//            System.out.println(result.getColumn(i).tauMin);
//            assertEquals(expected[i], result.getColumn(i));
        }

    }

    // tests the naive correlation coefficient computation between two time windows.
    @Test
    public void testCorrelationCoefficientNaive() throws Exception {

        TimeSeries[] series = new TimeSeries[]{
                new TimeSeries(1, new double[]{1,2,3,4,5}),                // A
                new TimeSeries(1, new double[]{4,2,6,-3,11}),              // B1
                new TimeSeries(1, new double[]{100,101,102,103,104}),      // B2
                new TimeSeries(1, new double[]{-50,-51,-52,-53,-54})       // B3
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

    public void compareWithEJML(){



    }

    //------------------------------------------------------------------------------------------------------------------
    // mean calculation
    //------------------------------------------------------------------------------------------------------------------

    // Simple calculation of the mean of time series values in a window
    @Test
    public void testMean() throws Exception {

        assertEquals(3, CrossCorrelation.mean(new TimeSeries(1, new double[]{1, 2, 3, 4, 5}), 0, 4), 1e-15);
        assertEquals(1, CrossCorrelation.mean(new TimeSeries(1, new double[]{1, 1, 1, 1, 1}), 0, 4), 1e-15);
        assertEquals(0, CrossCorrelation.mean(new TimeSeries(1, new double[]{0}), 0, 0), 1e-15);

    }

    // int subs are (in this setting) about 2.5x faster than double subs
//    @Test public void compareIntAndFloatSubtraction(){
//
//        int N = 100000;
//        int k = 100000;
//
//        int[] ints = new int[N];
//        double[] doubles = new double[N];
//        Random r = new Random(1l);
//        for (int i = 0; i < N; i++) {
//            ints[i] = r.nextInt();
//            doubles[i] = r.nextDouble();
//        }
//
//        long before = System.currentTimeMillis();
//        for (int i = 1; i < N; i++) {
//            for (int j = 0; j < k; j++) {
//                ints[i] -= ints[i-1];
//            }
//        }
//        System.out.println(String.format("time needed for int subs: %s [ms]", System.currentTimeMillis()-before));
//
//        before = System.currentTimeMillis();
//        for (int i = 1; i < N; i++) {
//            for (int j = 0; j < k; j++) {
//                doubles[i] -= doubles[i-1];
//            }
//        }
//        System.out.println(String.format("time needed for double subs [ms]: %s", System.currentTimeMillis()-before));
//
//    }

    // lookups are only 1.6 times faster than multiplications.
//    @Test public void compareMultiplicationToLookup(){
//
//        int N = 10000;
//        int k = 1000000;
//
//        // generate random data
//        int[][] table = new int[N][N];
//        int[] results = new int[N];
//
//        double[] doubles1 = new double[N];
//        double[] doubles2 = new double[N];
//        double[] resultsDouble = new double[N];
//
//        Random r = new Random(1l);
//        for (int i = 0; i < N; i++) {
//            for (int j = 0; j < N; j++) table[i][j] = r.nextInt();
//            doubles1[i] = r.nextDouble();
//            doubles2[i] = r.nextDouble();
//        }
//
//        long before = System.currentTimeMillis();
//        for (int i = 1; i < N-1; i++) {
//            for (int j = 0; j < k-1; j++) {
//                results[i] = table[i-1][i+1];
//            }
//        }
//        System.out.println(String.format("time needed for int lookups: %s", System.currentTimeMillis()-before));
//
//        before = System.currentTimeMillis();
//        for (int i = 1; i < N-1; i++) {
//            for (int j = 0; j < k-1; j++) {
//                resultsDouble[i] = doubles1[i-1] * doubles2[i+1];
//            }
//        }
//        System.out.println(String.format("time needed for double mults: %s", System.currentTimeMillis()-before));
//
//    }

    // Test the computation of the mean of a window from a previous window
    @Test
    public void testIncrementalMean() throws Exception {

        TimeSeries a = new TimeSeries(1, new double[]{1,2,3,4,5,6,7,8});

        double initialMean = CrossCorrelation.mean(a, 0, 2); // 2

        double incrementalMean = CrossCorrelation.incrementalMean(a, 1, 3, initialMean, 0);
        assertEquals(3, incrementalMean, 1e-15);

        incrementalMean = CrossCorrelation.incrementalMean(a, 2, 4, incrementalMean, 1);
        assertEquals(4, incrementalMean, 1e-15);

        incrementalMean = CrossCorrelation.incrementalMean(a, 3, 5, incrementalMean, 2);
        assertEquals(5, incrementalMean, 1e-15);

    }

    //------------------------------------------------------------------------------------------------------------------
    // test data generation routines
    //------------------------------------------------------------------------------------------------------------------

    @Test public void generateRandomTimeSeries() throws IOException {

        int timeSeriesLength = 5000;
        int numTimeSeries = 5;
        List<TimeSeries> ts = new ArrayList<>(numTimeSeries);
        for (int i = 0; i < numTimeSeries; i++) {
            TimeSeries randomTs = new TimeSeries(i+1, new double[timeSeriesLength], new double[timeSeriesLength]);
            for (int j = 0; j < timeSeriesLength; j++) {
                randomTs.getDataItems().re[j] = j;
                randomTs.getDataItems().im[j] = Math.random();
            }
            ts.add(randomTs);
        }

        FileModel.persist(ts, "./data/randomSeries.txt");

    }

    /**
     * Generates a random signal and a copy of that signal with a variable lag and a shift along the y axis applied to it.
     * @throws IOException
     */
    @Test public void generateVariableLagSeriesPair() throws IOException {

        int randomPoints = 300;
        int sampling = 4; // steps per random point
        int timeSeriesLength = (randomPoints-1)*sampling;
        double yOffset = 1;  // the second time series is shifted by that amount along the y axis

        // create reference time series as a brownian motion
        TimeSeries reference = new TimeSeries(1, new double[timeSeriesLength], new double[timeSeriesLength]);
        reference.getDataItems().re[0] = 0;
        reference.getDataItems().im[0] = 0;
        double lastVal = 0;
        double nextVal = Math.random();
        for (int j = 1; j < timeSeriesLength; j++) {
//            if(j%sampling == 0) {
//                lastVal=nextVal;
//                nextVal = Math.random();
//            }

            reference.getDataItems().re[j] = j;
            reference.getDataItems().im[j] = reference.getDataItems().im[j-1] + 2*Math.random()-1;    // brownian
//            reference.getDataItems().im[j] = Math.sin(1.*j/timeSeriesLength*10 * 2*Math.PI);             // sine
//            reference.getDataItems().im[j] = Math.random();                                           // random
//            reference.getDataItems().im[j] = (nextVal-lastVal/sampling);                                // repeat each value sample times
        }

        // create lagged version. the lag starts at j lag, falls to zero at 1/3 of the signal, stays zero until 2/3 of the signal
        // and falls to min lag until 3/3 of the signal are reached
        double maxLag = 100;
        double minLag = -100;
        TimeSeries lagged = new TimeSeries(2, new double[timeSeriesLength], new double[timeSeriesLength]);
        int lag = 0;
        for (int j = 0; j < timeSeriesLength; j++) {
            double progress = 1.*j/timeSeriesLength;
            if(progress > 2./3){
                double progressInSection = (progress-2./3) * 3;
                lag = (int) Math.round(progressInSection * minLag);
            } else if( progress > 1./3 ){
                lag = 0;
            } else {
                double progressInSection = progress * 3;
                lag = (int) Math.round(progressInSection * maxLag);
            }
            lagged.getDataItems().re[j] = j;
            System.out.println(lag);
            lagged.getDataItems().im[j] = reference.getDataItems().im[j+lag];
        }


        FileModel.persist(Arrays.asList(reference, lagged), "./data/laggedSeries.txt");

    }

    //------------------------------------------------------------------------------------------------------------------
    // performance tests
    //------------------------------------------------------------------------------------------------------------------

//    Fisher transformation requries computation of either atan or log.
//    The natural logarithm requires usually only 65% of the time used when invoking the commons.math atanh function.
//    The lookup usually requires 20% to 25% of the time used when computing natural logarithms.
    @Test public void atanVsLn(){

        int computations = 10000000;

        // calc random correlation coefficients
        double[] correlationCoeffs = new double[computations];  // also used to store the results of the table lookup method to save memory
        double[] outcomes = new double[computations];           // used to store the results of the logarihm method

        for (int i = 0; i < computations; i++) {
            correlationCoeffs[i] = 2*Math.random()-1;
        }
        long before;

        // calc fisher transformation using natural logarithm
        before = System.currentTimeMillis();
        for (int i = 0; i < computations; i++) {
            outcomes[i] = 0.5 * Math.log((1+correlationCoeffs[i])/(1-correlationCoeffs[i]));
//            the following is much slower (takes 3 times as long!) although there's a specialized function (but the precision is higher for values close to 1)
//            outcomes[i%100] = 0.5 * Math.log1p(correlationCoeffs[i]) - Math.log1p(-correlationCoeffs[i]);
        }
        System.out.println("Natural logarithm: "+(System.currentTimeMillis()-before));

        // calc fisher transformation using precomputed arcus tangens hyperbolicus takes only about 20% to 25% of the time spent with the logarithms.
        // first half refers to negative correlation values, second to positive values
        // using 100.000 samples, approximately 0.9874% of the results will have a precision of at least 5 digits (95% of them 5,6 or 7)
        // 1% will have only 3 digits precision, in rare cases the result is -Infinity!
        // using floats doesn't effect the precision, because we're talking about much lower precision levels than 1e-15
        int samples = 100000;
        Atanh atanh = new Atanh();
        float[] atanhTable = new float[2*samples+1]; // atanhTable[0] is for -1, atanhTable[samples] is for input 0, atanhTable[2*samples+1] is for +1
        for (int i = 0; i < atanhTable.length; i++) {
            float functionArgument = -1.f + 1.f*i/samples;
            atanhTable[i] = 0.5f * (float)Math.log((1+functionArgument)/(1-functionArgument));//atanh.value(functionArgument);
        }

        before = System.currentTimeMillis();
        for (int i = 0; i < computations; i++) {
            int offset = (int) Math.round((correlationCoeffs[i]+1.)*samples);
            correlationCoeffs[i] = atanhTable[offset];
        }
        System.out.println("Atanh: "+(System.currentTimeMillis()-before));

        int[] precisionHistogram = new int[20];
        // check precision of table output
        for (int i = 0; i < computations; i++) {
            int decimalsPrecision = (int)-Math.floor(Math.log10(Math.abs(outcomes[i]-correlationCoeffs[i])));
            if(decimalsPrecision<0){
                precisionHistogram[0]++;
            } else {
                precisionHistogram[decimalsPrecision]++;

            }

//            assertEquals(outcomes[i], correlationCoeffs[i], 1e-1);
        }
        System.out.println(Arrays.toString(precisionHistogram));

    }

    @Test public void testTDistribution(){

        TDistribution tDistribution = new TDistribution(13);
        double[] alphas = new double[]{0.1,0.05,0.025,0.01,0.005,0.0005};
        double[] values = new double[]{1.350,1.771,2.160,2.650,3.012,4.221};
        for (int i = 0; i <alphas.length; i++) {
            System.out.println(String.format("%s", /*alphas[i],*/
                    1
                    -tDistribution.cumulativeProbability(values[i])
                    +tDistribution.cumulativeProbability(-values[i])
            ));
        }

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