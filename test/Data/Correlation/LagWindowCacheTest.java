package Data.Correlation;

import Data.TimeSeries;
import Data.TimeSeriesTest;
import Data.Windowing.WindowMetadata;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class LagWindowCacheTest {

    /** Tests basic ring buffer behavior. */
    @Test public void testRingBuffering(){

        final WindowMetadata someMetadata = new WindowMetadata(Arrays.asList(new TimeSeries(0)), Arrays.asList(new TimeSeries(0), new TimeSeries(1)), 1, -1, 1, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1);
        final int cacheSize = 3;
        LagWindowCache cache = new LagWindowCache(someMetadata, cacheSize);

        // cache doesn't have windows 0..2
        for (int i = 0; i < 3; i++) assertFalse(cache.hasWindow(0, i));

        // put data for window start index 75
        cache.put(0, 75, new double[]{1}, 1);

        // cache has nothing but that
        for (int i = 70; i < 80; i++) assert((i == 75) == cache.hasWindow(0, i));
        // values are correct
        assertArrayEquals(new double[]{1}, cache.getNormalizedValues(0, 75), 1e-15);
        assertEquals(1, cache.getRootOfSummedSquares(0, 75), 1e-15);

        // fill until 'overflow' (add four values)
        // this leads to a range start of 77 (two fit, two overflow)
        cache.put(0, 76, new double[]{2}, 2);
        cache.put(0, 77, new double[]{3}, 3);
        cache.put(0, 78, new double[]{4}, 4);
        cache.put(0, 79, new double[]{5}, 5);

        // check that the windows for 77,78,79 contain 3,4,5
        for (int i = 3; i <= 5; i++){
            int windowIndex = 77 + (i-3);
            assertArrayEquals(new double[]{i}, cache.getNormalizedValues(0, windowIndex), 1e-15);
            assertEquals(i, cache.getRootOfSummedSquares(0, windowIndex), 1e-15);
        }

        cache.put(0, 80, new double[]{6}, 6);

        // check that the windows for 78,79,80 contain 4,5,6
        for (int i = 4; i <= 6; i++){
            int windowIndex = 78 + (i-4);
            assertArrayEquals(new double[]{i}, cache.getNormalizedValues(0, windowIndex), 1e-15);
            assertEquals(i, cache.getRootOfSummedSquares(0, windowIndex), 1e-15);
        }

        // check that cache doesn't have 77
        assertFalse(cache.hasWindow(0, 77));

    }

    /** Tests correct computation of not yet cached windows. */
    @Test public void testRequestNonExisting() {

//        TimeSeries tsA = new TimeSeries(1, 2, 3, 4, 5, 6);
//        TimeSeries tsB = new TimeSeries(10, 13, 16, 19, 22, 25);
//        TimeSeries tsC = new TimeSeries(-2, 0, 2, 0, -2, 0);
//        List<TimeSeries> setA = Arrays.asList(tsA);
//        final List<TimeSeries> setB = Arrays.asList(tsA, tsB, tsC);
        int numTimeSeries = 100;
        int timeSeriesLength = 10000;

        List<TimeSeries> set = TimeSeriesTest.randomTimeSerieses(numTimeSeries, timeSeriesLength);

        int windowSize = 3, baseWindowOffset = 1;
        WindowMetadata metadata = new WindowMetadata(set, set, windowSize, -1, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, baseWindowOffset);

        LagWindowCache cache = new LagWindowCache(metadata, 3);

        for (int ts = 0; ts < numTimeSeries; ts++) {
            TimeSeries current = metadata.setB.get(ts);
            for (int i = 0; i < timeSeriesLength-windowSize-1; i++) {

                assertFalse(cache.hasWindow(ts, i));
                double[] normalizedValues = cache.getNormalizedValues(ts, i);
                double rootOfSummedSquares = cache.getRootOfSummedSquares(ts, i);
                assertTrue(cache.hasWindow(ts, i));

                double[] windowValues = Arrays.copyOfRange(current.getDataItems().im, i, i+windowSize);
                DescriptiveStatistics windowStats = new DescriptiveStatistics(windowValues);
                DescriptiveStatistics expectedNormalizedValues = new DescriptiveStatistics();
                for (int t = 0; t < windowValues.length; t++) {
                    expectedNormalizedValues.addValue(windowValues[t] - windowStats.getMean());
                }

                assertArrayEquals(expectedNormalizedValues.getValues(), normalizedValues, 1e-13);
                assertEquals(Math.sqrt(expectedNormalizedValues.getSumsq()), rootOfSummedSquares, 1e-13);
            }
        }

    }

}