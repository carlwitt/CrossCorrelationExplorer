package Data.Correlation;

import Data.TimeSeries;
import Data.TimeSeriesTest;
import Data.Windowing.WindowMetadata;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CorrelationMatrixTest {

    // tests the correct precomputation of the means and roots of summed squares of all windows
    @Test public void testPrecomputeData(){

        TimeSeries ts1 = new TimeSeries(1, 1,10,7,4,3,12);
        TimeSeries ts2 = new TimeSeries(1, 1,2,3,2,0,1);

        int windowSize = 3, baseWindowOffset = 1, tauMin = 0, tauMax = 0;
        WindowMetadata metadata = new WindowMetadata(ts1, ts2, windowSize, tauMin, tauMax, 1, baseWindowOffset);
        CorrelationMatrix.setSignificanceLevel(metadata, 0.05);

        CorrelationMatrix cc = new CorrelationMatrix(metadata);
        cc.precomputeTerms();

        double[] expectedMeansA = new double[]{6.0, 7.0, 4.666666666666666, 6.333333333333332, Double.NaN, Double.NaN};
        double[] expectedMeansB = new double[]{2.0, 2.3333333333333335, 1.666666666666667, 1.0000000000000002, Double.NaN, Double.NaN};
        double[] expectedL2NormsA = new double[]{6.48074069840786, 4.242640687119285, 2.9439202887759492, 6.97614984548545, Double.NaN, Double.NaN};
        double[] expectedL2NormsB = new double[]{1.4142135623730951, 0.816496580927726, 2.1602468994692865, 1.4142135623730951, Double.NaN, Double.NaN};

        assertArrayEquals(expectedMeansA, cc.meansA[0], 1e-15);
        assertArrayEquals(expectedMeansB, cc.meansB[0], 1e-15);
        assertArrayEquals(expectedL2NormsA, cc.L2NormsA[0], 1e-15);
        assertArrayEquals(expectedL2NormsB, cc.L2NormsB[0], 1e-15);

    }

    @Test public void testCompareWithNaive() {

        int numTimeSeries = 50;
        int length = 100;

        List<TimeSeries> tsA = TimeSeriesTest.randomTimeSeries(numTimeSeries, length);
        List<TimeSeries> tsB = TimeSeriesTest.randomTimeSeries(numTimeSeries, length);

//        TimeSeries tsA = new TimeSeries(1, 1,2,3,4,4,3,2,1);
//        TimeSeries tsB = new TimeSeries(1, 4,3,2,1,1,2,3,4);

//        List<TimeSeries> setA = Arrays.asList(ts.get(0), ts.get(2));
//        List<TimeSeries> setB = Arrays.asList(ts.get(3), ts.get(8));

        int windowSize = 4, baseWindowOffset = 2, tauMin = -1, tauMax = 3, tauStep = 2;
//        WindowMetadata metadata = new WindowMetadata(tsA, tsB, windowSize, tauMin, tauMax, tauStep, baseWindowOffset);
        WindowMetadata metadata = new WindowMetadata(tsA, tsB, windowSize, tauMin, tauMax, tauStep, baseWindowOffset);

        CorrelationMatrix.setSignificanceLevel(metadata, 0.05);

        long before = System.currentTimeMillis();

        CorrelationMatrix expected = CrossCorrelation.naiveCrossCorrelation(metadata);

        System.out.println(String.format("naive: %s", System.currentTimeMillis() - before));

        before = System.currentTimeMillis();
        CorrelationMatrix result = new CorrelationMatrix(metadata);
        result.compute();

        System.out.println(String.format("mem: %s", System.currentTimeMillis()-before));

//        System.out.println("Expected: "+expected);
//        System.out.println("Result: "+result);

        for (int STAT = 0; STAT < CorrelationMatrix.NUM_STATS; STAT++) {
            assertEquals(expected.getMin(STAT), result.getMin(STAT), 1e-15);
            assertEquals(expected.getMax(STAT), result.getMax(STAT), 1e-15);
        }

        for (int i = 0; i < expected.columns.size(); i++) {
            CorrelationMatrix.CorrelationColumn expectedColumn = expected.getColumn(i);
            CorrelationMatrix.CorrelationColumn resultColumn = result.getColumn(i);
            for (int stat = 0; stat < CorrelationMatrix.NUM_STATS; stat++)
                assertArrayEquals(expectedColumn.data[stat], resultColumn.data[stat], 1e-15);
        }

    }

    @Test public void testCompute() throws Exception {

        TimeSeries ts1 = new TimeSeries(1, 1,10,7,4,3,12);
        TimeSeries ts2 = new TimeSeries(1, 1,2,3,2,0,1);
        TimeSeries ts3 = new TimeSeries(1, 2,3,4,-5,-3,0);

        // crossCorrelation({A}, {B,C})
        List<TimeSeries> setA = Arrays.asList(ts1);
        List<TimeSeries> setB = Arrays.asList(ts2,ts3);

        int windowSize = 3, baseWindowOffset = 1, tauMin = 0, tauMax = 0;
        WindowMetadata metadata = new WindowMetadata(setA, setB, windowSize, tauMin, tauMax, 1, baseWindowOffset);
        CorrelationMatrix.setSignificanceLevel(metadata, 0.05);

        CorrelationMatrix instance = new CorrelationMatrix(metadata);
        instance.compute();

        for(CorrelationMatrix.CorrelationColumn c : instance.getResultItems()){
            System.out.println(c);
        }

        System.out.println(String.format("%s", instance));
//        System.out.println(String.format("correlogram store\n%s", CorrelogramStore.correlationMatricesByMetadata.entrySet()));
        assertEquals(1, instance.getColumn(0).data[CorrelationMatrix.MEAN][0], 1e-5);
        assertEquals(4, instance.getColumn(1).data[CorrelationMatrix.MEAN][0], 1e-5);
        assertEquals(9, instance.getColumn(2).data[CorrelationMatrix.MEAN][0], 1e-5);

        // from wolfram alpha
        assertEquals(1.33333, instance.getColumn(0).data[CorrelationMatrix.STD_DEV][0], 1e-4);
        assertEquals(2.40370, instance.getColumn(1).data[CorrelationMatrix.STD_DEV][0], 1e-4);
        assertEquals(3.52766, instance.getColumn(2).data[CorrelationMatrix.STD_DEV][0], 1e-4);

    }


    @Test public void testPerformance() {

        int numTimeSeries = 20;
        int length = 1000;

        List<TimeSeries> tsA = TimeSeriesTest.randomTimeSeries(numTimeSeries, length);
        List<TimeSeries> tsB = TimeSeriesTest.randomTimeSeries(numTimeSeries, length);

        int windowSize = 200, baseWindowOffset = 30, tauMin = -100, tauMax = 100, tauStep = 1;
        WindowMetadata metadata = new WindowMetadata(tsA, tsB, windowSize, tauMin, tauMax, tauStep, baseWindowOffset);
        CorrelationMatrix.setSignificanceLevel(metadata, 0.05);

        long before = System.currentTimeMillis();
        CorrelationMatrix result = new CorrelationMatrix(metadata);
        result.compute();

        System.out.println(String.format("mem: %s", System.currentTimeMillis()-before));

    }

}