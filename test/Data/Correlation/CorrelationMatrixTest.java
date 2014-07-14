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

        int numTimeSeries = 1000;
        int length = 11;

        List<TimeSeries> ts = TimeSeriesTest.randomTimeSerieses(numTimeSeries, length);

        List<TimeSeries> setA = Arrays.asList(ts.get(0), ts.get(2));
        List<TimeSeries> setB = Arrays.asList(ts.get(3), ts.get(8));

//        List<TimeSeries> setA = Arrays.asList(new TimeSeries(-1,3,2,15,21,9,6,-2), new TimeSeries(-2,2,1,14,20,8,5,-3));
//        List<TimeSeries> setB = Arrays.asList(new TimeSeries(-10,30,20,150,210,90,60,-20), new TimeSeries(-10,30,20,150,210,90,60,-20));

//        List<TimeSeries> setA = new ArrayList<>(numTimeSeries/2), setB = new ArrayList<>(numTimeSeries/2);
//        for (int i = 0; i < numTimeSeries / 2; i++) {
//            setA.add(ts.get(i));
//            setB.add(ts.get(i+numTimeSeries/2));
//        }

        int windowSize = 10, baseWindowOffset = 3, tauMin = -2, tauMax = 2;
        WindowMetadata metadata = new WindowMetadata(setA, setB, windowSize, tauMin, tauMax, baseWindowOffset);
        CorrelationMatrix.setSignificanceLevel(metadata, 0.05);

        CorrelationMatrix expected = CrossCorrelation.naiveCrossCorrelation(metadata);

        System.out.println("Expected: "+expected);

        CorrelationMatrix result = new CorrelationMatrix(metadata);
        result.compute();

        System.out.println("Result: "+result);

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
        List<TimeSeries> setA = Arrays.asList(new TimeSeries[]{ts1});
        List<TimeSeries> setB = Arrays.asList(new TimeSeries[]{ts2,ts3});

        int windowSize = 3, baseWindowOffset = 1, tauMin = 0, tauMax = 0;
        WindowMetadata metadata = new WindowMetadata(setA, setB, windowSize, tauMin, tauMax, baseWindowOffset);

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

    @Test public void testColumnCorrelations() throws Exception {

    }

    @Test public void testEquals() throws Exception {



    }
}