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

    @Test public void compareWithNaive() {

        int numTimeSeries = 4;
        int length = 12;

        List<TimeSeries> ts = TimeSeriesTest.randomTimeSerieses(numTimeSeries, length);

//        List<TimeSeries> setA = Arrays.asList(ts.get(0), ts.get(1));
//        List<TimeSeries> setB = Arrays.asList(ts.get(2), ts.get(3));

        List<TimeSeries> setA = Arrays.asList(new TimeSeries(-1,3,2,15,21,9,6,-2), new TimeSeries(-2,2,1,14,20,8,5,-3));
        List<TimeSeries> setB = Arrays.asList(new TimeSeries(-10,30,20,150,210,90,60,-20), new TimeSeries(-10,30,20,150,210,90,60,-20));

        int windowSize = 5, baseWindowOffset = 3, tauMin = -1, tauMax = 2;
        WindowMetadata metadata = new WindowMetadata(setA, setB, windowSize, tauMin, tauMax, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, baseWindowOffset);
        CorrelationMatrix.setSignificanceLevel(metadata, 0.05);

        CorrelationMatrix expected = CrossCorrelation.naiveCrossCorrelation(metadata);

        System.out.println("Expected: "+expected);

        CorrelationMatrix result = new CorrelationMatrix(metadata);
        result.compute();

        System.out.println("Result: "+result);

        for (int i = 0; i < expected.columns.size(); i++) {
            CorrelationMatrix.CorrelationColumn expectedColumn = expected.getColumn(i);
            CorrelationMatrix.CorrelationColumn resultColumn = result.getColumn(i);
            assertArrayEquals(expectedColumn.mean, resultColumn.mean, 1e-15);
            assertArrayEquals(expectedColumn.stdDev, resultColumn.stdDev, 1e-15);
        }

    }

    @Test public void testCompute() throws Exception {

        TimeSeries ts1 = new TimeSeries(1,10,7,4,3,12);
        TimeSeries ts2 = new TimeSeries(1,2,3,2,0,1);
        TimeSeries ts3 = new TimeSeries(2,3,4,-5,-3,0);

        // crossCorrelation({A}, {B,C})
        List<TimeSeries> setA = Arrays.asList(new TimeSeries[]{ts1});
        List<TimeSeries> setB = Arrays.asList(new TimeSeries[]{ts2,ts3});

        int windowSize = 3, baseWindowOffset = 1, tauMin = 0, tauMax = 0;
        WindowMetadata metadata = new WindowMetadata(setA, setB, windowSize, tauMin, tauMax, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, baseWindowOffset);

        CorrelationMatrix instance = new CorrelationMatrix(metadata);
        instance.compute();

        for(CorrelationMatrix.CorrelationColumn c : instance.getResultItems()){
            System.out.println(c);
        }

        System.out.println(String.format("%s", instance));
//        System.out.println(String.format("correlogram store\n%s", CorrelogramStore.correlationMatricesByMetadata.entrySet()));
        assertEquals(1, instance.getColumn(0).mean[0], 1e-5);
        assertEquals(4, instance.getColumn(1).mean[0], 1e-5);
        assertEquals(9, instance.getColumn(2).mean[0], 1e-5);

        // from wolfram alpha
        assertEquals(1.33333, instance.getColumn(0).stdDev[0], 1e-4);
        assertEquals(2.40370, instance.getColumn(1).stdDev[0], 1e-4);
        assertEquals(3.52766, instance.getColumn(2).stdDev[0], 1e-4);
        
    }

    @Test public void testColumnCorrelations() throws Exception {

    }

    @Test public void testAggregate() throws Exception {

    }
}