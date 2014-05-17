package Data.Correlation;

import Data.TimeSeries;
import org.junit.Test;

import static org.junit.Assert.*;

public class BaseWindowStatisticsTest {

    TimeSeries ts = new TimeSeries(new double[]{1,2,3,4,5,6,7,8,9});
    TimeSeries ts2 = new TimeSeries(new double[]{0.173935289029032, 0.845529697602615, 0.729925303952768, 0.251001720316708, 0.735257865395397, 0.201331031974405, 0.58229084732011, 0.563765180762857, 0.405128265731037});

    @Test
    public void testGetWindowStartIndices() throws Exception {

        // for delta = 1 there's a base window at 0..N-|w|
        BaseWindowStatistics s = new BaseWindowStatistics(ts, 3, 1);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6}, s.getWindowStartIndices());

        // for |w| = N there's only one window at index 0
        s = new BaseWindowStatistics(ts, 9, 1);
        assertArrayEquals(new int[]{0}, s.getWindowStartIndices());

        // for delta = 1 and |w| = 1 there's a window at 0..N-1
        s = new BaseWindowStatistics(ts, 1, 1);
        assertArrayEquals(new int[]{0,1,2,3,4,5,6,7,8}, s.getWindowStartIndices());

        // for delta = k there's a window at 0*k, 1*k, ...
        s = new BaseWindowStatistics(ts, 1, 4);
        assertArrayEquals(new int[]{0, 4, 8}, s.getWindowStartIndices());

    }

    @Test
    public void testGetWindowNumberForStartIndex() throws Exception {

        // for baseWindowOffset = 1, the window numbers equal the window start indices
        BaseWindowStatistics s = new BaseWindowStatistics(ts, 3, 1);
        for (int i = 0; i <= 6; i++) assertEquals(i, s.getWindowNumberForStartIndex(i));

        // for baseWindowOffset = k, at 0*k is the 1st window (index 0), at 1*k is the second window (index 1), ...
        int delta = 4;
        s = new BaseWindowStatistics(ts, 1, delta);
        assertArrayEquals(new int[]{0, 4, 8}, s.getWindowStartIndices());
        for (int i = 0; i <= 2; i++) assertEquals(i, s.getWindowNumberForStartIndex(i*delta));

    }

    @Test
    public void testGetNormalizedValues() throws Exception {

        int windowSize = 4, delta = 2;
        BaseWindowStatistics s = new BaseWindowStatistics(ts, windowSize, delta);

        // test with simple data
        double[] expectedValues = new double[]{-1.5,-0.5,0.5,1.5};
        assertArrayEquals(expectedValues, s.getNormalizedValues(0), 1e-15);
        assertArrayEquals(expectedValues, s.getNormalizedValues(2), 1e-15);
        assertArrayEquals(expectedValues, s.getNormalizedValues(4), 1e-15);

        // test with random data
        s = new BaseWindowStatistics(ts2, windowSize, delta);
        double[][] normalizedValues = new double[][]{
                new double[]{-0.326162713696249, 0.345431694877334, 0.229827301227488, -0.249096282408573},
                new double[]{0.250546323542949, -0.228377260093112, 0.255878884985577, -0.278047948435415},
                new double[]{0.214596634032205, -0.319330199388787, 0.0616296159569174, 0.043103949399665}};

        assertArrayEquals(normalizedValues[0], s.getNormalizedValues(0), 1e-15);
        assertArrayEquals(normalizedValues[1], s.getNormalizedValues(2), 1e-15);
        assertArrayEquals(normalizedValues[2], s.getNormalizedValues(4), 1e-15);

    }

    @Test
    public void getSummedSquares(){

        int windowSize = 4, delta = 2;
        BaseWindowStatistics s = new BaseWindowStatistics(ts, windowSize, delta);

        // test with simple data
        double squaredSum = 5;
        assertEquals(squaredSum, s.getSummedSquares(0), 1e-15);
        assertEquals(squaredSum, s.getSummedSquares(2), 1e-15);
        assertEquals(squaredSum, s.getSummedSquares(4), 1e-15);

        // test with random data
        s = new BaseWindowStatistics(ts2, windowSize, delta);
        assertEquals( 0.34057471793081, s.getSummedSquares(0), 1e-15);
        assertEquals( 0.25771429857913, s.getSummedSquares(2), 1e-15);
        assertEquals( 0.153679651596481, s.getSummedSquares(4), 1e-15);

    }
}