/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Data.Correlation;

import Data.DataModel;
import Data.TimeSeries;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Carl Witt
 */
public class CorrelationMatrixTest {

    @Before
    public void setUp() {
        CorrelogramStore.clear();
    }

//    THE Cross-Correlation definition has been changed so this test can not be used anymore!
//    TODO: Test the aggregation of multiple correlograms (correct computation of mean/standard dev)
//    /**
//     * Test of aggregation constructor, i.e. the computation of mean and standard deviation.
//     */
//    @Test public void testAggregateConstructor() {
//        System.out.println("aggregateConstructor");
//
//        int windowSize = 1, baseWindowOffset = 1, tauMin = 0, tauMax = 0;
//        TimeSeries ts1 = new TimeSeries(1, new double[]{0,1,2}, new double[]{1,1,1});   // A: 1,1,1
//        TimeSeries ts2 = new TimeSeries(2, new double[]{0,1,2}, new double[]{1,2,3});   // B: 1,2,3
//        TimeSeries ts3 = new TimeSeries(3, new double[]{0,1,2}, new double[]{2,3,4});   // C: 2,3,4
//
////        dataModel.put(1, ts1);
////        dataModel.put(2, ts2);
////        dataModel.put(3, ts3);
//
//        // crossCorrelation({A}, {B,C})
//        List<TimeSeries> setA = Arrays.asList(new TimeSeries[]{ts1});
//        List<TimeSeries> setB = Arrays.asList(new TimeSeries[]{ts2,ts3});
//        CorrelationMetadata metadata = new CorrelationMetadata(setA, setB, windowSize, tauMin, tauMax, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, baseWindowOffset);
//        CorrelationMatrix instance = new CorrelationMatrix(metadata);
//        instance.compute();
//
//        System.out.println(String.format("%s", instance));
////        System.out.println(String.format("correlogram store\n%s", CorrelogramStore.correlationMatricesByMetadata.entrySet()));
//        assertEquals(1, instance.columns.get(0).mean[0], 1e-5);
//        assertEquals(4, instance.columns.get(1).mean[0], 1e-5);
//        assertEquals(9, instance.columns.get(2).mean[0], 1e-5);
//
//        // from wolfram alpha
//        assertEquals(1.33333, instance.columns.get(0).stdDev[0], 1e-4);
//        assertEquals(2.40370, instance.columns.get(1).stdDev[0], 1e-4);
//        assertEquals(3.52766, instance.columns.get(2).stdDev[0], 1e-4);
//    }

//    Columns have a minTau attribute now which explicitly denotes the time lag that is represented by their first column entry.
//    This makes manual wrapping unnecessary.
//    @Test public void testSplitLagEven() {
//        // time lags 0 ... 5 should be split rendered -2, -1, ..., 3
//        int[] timeLags = new int[]{0, 1, 2, 3, 4, 5};
//        int[] renderAs = new int[]{0, 1, 2, 3, -2, -1};
//
//        for (int i = 0; i < timeLags.length; i++) {
//            assertEquals(renderAs[i], CorrelationMatrix.splitLag(timeLags[i], timeLags.length));
//        }
//
//        assertEquals(-2, CorrelationMatrix.minLag(6));
//        assertEquals(3, CorrelationMatrix.maxLag(6));
//    }
//
//    @Test public void testSplitLagOdd() {
//        // time lags 0 ... 5 should be split rendered -2, -1, ..., 3
//        int[] timeLags = new int[]{0, 1, 2, 3, 4, 5, 6};
//        int[] renderAs = new int[]{0, 1, 2, 3, -3, -2, -1};
//
//        for (int i = 0; i < timeLags.length; i++) {
//            assertEquals(renderAs[i], CorrelationMatrix.splitLag(timeLags[i], timeLags.length));
//        }
//
//        assertEquals(-3, CorrelationMatrix.minLag(7));
//        assertEquals(3, CorrelationMatrix.maxLag(7));
//    }

}