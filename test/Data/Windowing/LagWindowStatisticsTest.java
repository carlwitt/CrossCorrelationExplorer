package Data.Windowing;

import Data.TimeSeries;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class LagWindowStatisticsTest {

    /** Tests the correct derivation of properties (such as shared windows, window overlap) from the input data. */
    @Test public void testConstructedProperties(){

        TimeSeries ts = new TimeSeries(1, new double[9]);

        // perfect fit: time series length is a multiple of the window length
        // continuous: untion of lag window ranges has no holes

        // continuous, non-perfect fit
        LagWindowStatistics instance = new LagWindowStatistics(ts, 4, 3, -1, 3);
        assertEquals(6, instance.numWindows);
        assertEquals(2, instance.sharedLagWindows);
        assertEquals(true, instance.continuousIndices);

        // non-contiuous, non-perfect fit
        instance = new LagWindowStatistics(ts, 4, 3, 0, 1);
        assertEquals(4, instance.numWindows);
        assertEquals(-1, instance.sharedLagWindows);
        assertEquals(false, instance.continuousIndices);

        // continuous, perfect fit
        instance = new LagWindowStatistics(ts, 3, 3, -3, 3);
        assertEquals(7, instance.numWindows);
        assertEquals(4, instance.sharedLagWindows);
        assertEquals(true, instance.continuousIndices);

        // non-continuous, perfect fit, base window not in lag range
        instance = new LagWindowStatistics(ts, 3, 3, 1, 2);
        assertEquals(7, instance.numWindows);
        assertEquals(-1, instance.sharedLagWindows);
        assertEquals(false, instance.continuousIndices);
        // like above but with mirrored lag range
        instance = new LagWindowStatistics(ts, 3, 3, -2, -1);
        assertEquals(7, instance.numWindows);
        assertEquals(-1, instance.sharedLagWindows);
        assertEquals(false, instance.continuousIndices);
    }

    @Test public void testWindowStartIndices(){

        TimeSeries ts = new TimeSeries(1, new double[9]);

        int tauMin = -1, tauMax = 3, delta = 3, windowSize = 4;
        LagWindowStatistics instance = new LagWindowStatistics(ts, windowSize, delta, tauMin, tauMax);

        int[] expectedWindowStartIndices = new int[]{0,1,2,3,4,5};
        assertArrayEquals(expectedWindowStartIndices, instance.getWindowStartIndices());

    }

    @Test public void testStatisticsForWindows(){

        TimeSeries ts = new TimeSeries(1, new double[]{1,2,3,4,5,6,7,8,9});

        int tauMin = -1, tauMax = 3, delta = 3, windowSize = 4;
        LagWindowStatistics instance = new LagWindowStatistics(ts, windowSize, delta, tauMin, tauMax);

        for (int i = 0; i < 6; i++) {
            double expectedMean = 2.5 + i;
            assertEquals(expectedMean, instance.means[i], 1e-15);
            assertEquals(Math.sqrt(5), instance.rootOfSummedSquares[i], 1e-15);
            for (int j = 0; j < windowSize; j++) {
                assertEquals(ts.getDataItems().im[i+j]-expectedMean, instance.getNormalizedValues(i)[j], 1e-15);
            }
        }

        ts = new TimeSeries(1, new double[]{0.13193934, 0.28683374, 0.72948551, 0.60793923, 0.27034457, 0.24088859, 0.74299995, 0.03423623, 0.47228535});
        instance = new LagWindowStatistics(ts, windowSize, delta, tauMin, tauMax);
        double[] expectedMeans = new double[]{0.4390495,0.4736508,0.4621645,0.4655431,0.3221173,0.3726025};
        double[] expectedSummedSquares = new double[]{0.2303631,0.1597188,0.1784687,0.1858311,0.2692962,0.2789712};

        for (int i = 0; i < 6; i++) {
            assertEquals(expectedMeans[i], instance.means[i], 1e-7);
            assertEquals(Math.sqrt(expectedSummedSquares[i]), instance.rootOfSummedSquares[i], 1e-7);
            for (int j = 0; j < windowSize; j++) {
                assertEquals(ts.getDataItems().im[i+j]-expectedMeans[i], instance.getNormalizedValues(i)[j], 1e-7);
            }
        }

    }


}