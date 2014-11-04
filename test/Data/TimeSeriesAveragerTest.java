package Data;

import javafx.collections.FXCollections;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;

public class TimeSeriesAveragerTest {

    @Test public void testComputeSOP() {
        double[] xValues = new double[]{1, 2, 3, 4, 5, 6, 7, 8};
        double[] yValues1 = new double[]{0, 2, 2, 2, 2, 1, 0, 1};
        double[] yValues2 = new double[]{2, 0, 0, 2, 2, Double.NaN, 1, 2};

        TimeSeries ts1 = new TimeSeries(1, xValues, yValues1);
        TimeSeries ts2 = new TimeSeries(2, xValues, yValues2);

        TimeSeriesAverager timeSeriesAverager = new TimeSeriesAverager(Arrays.asList(ts1, ts2));
        timeSeriesAverager.setGroupSize(1);
        timeSeriesAverager.numBins = 2;

//        System.out.println(String.format("yVals1: %s", Arrays.toString(Arrays.stream(timeSeriesAverager.getYValues(1)).map(d -> Precision.round(d, 3)).toArray())));
//        System.out.println(String.format("yVals2: %s", Arrays.toString(Arrays.stream(timeSeriesAverager.getYValues(2)).map(d -> Precision.round(d, 3)).toArray())));

        float[] expectedMinValues = new float[]{0, 0, 0, 2, 2, 1, 0, 1};
        float[] expectedMaxValues = new float[]{2, 2, 2, 2, 2, 1, 1, 2};
        short[][][] expectedHistograms = new short[][][]{
                new short[][]{new short[]{0, 1},
                        new short[]{1, 0}},
                new short[][]{new short[]{1, 0},
                        new short[]{0, 1}},
                new short[][]{new short[]{1, 0},
                        new short[]{1, 0}},
                new short[][]{new short[]{2, 0},
                        new short[]{0, 0}},
                new short[][]{new short[]{1, 0},
                        new short[]{0, 0}},
                new short[][]{new short[]{1, 0},
                        new short[]{0, 0}},
                new short[][]{new short[]{1, 0},
                        new short[]{0, 1}}};

        assertTrue(Arrays.deepEquals(expectedHistograms, timeSeriesAverager.histograms));
        assertTrue(Arrays.equals(expectedMinValues, timeSeriesAverager.minValues));
        assertTrue(Arrays.equals(expectedMaxValues, timeSeriesAverager.maxValues));

        // print histograms
//        Arrays.stream(timeSeriesAverager.histograms).forEach(histogram -> {
//            for (short[] row : histogram) {
//                for (int col = 0; col < histogram.length; col++) System.out.print(row[col] + " ");
//                System.out.println();
//            }
//            System.out.println();
//        });
    }

    @Test public void testCompute() throws Exception {
        TimeSeries ts1 = new TimeSeries(1, new double[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, new double[]{2, 2, 3, 3, 4, 4, 5, 5, 6, 6});
        TimeSeries ts2 = new TimeSeries(2, new double[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});

        TimeSeriesAverager timeSeriesAverager = new TimeSeriesAverager(FXCollections.observableArrayList(ts1, ts2));
        timeSeriesAverager.setGroupSize(4);

        String xVals = Arrays.toString(timeSeriesAverager.getXValues());
        String yVals1 = Arrays.toString(timeSeriesAverager.getYValues(1));
        String yVals2 = Arrays.toString(timeSeriesAverager.getYValues(2));
        System.out.println(String.format("xVals: %s", xVals));
        System.out.println(String.format("yVals1: %s", yVals1));
        System.out.println(String.format("yVals2: %s", yVals2));
    }

    @Test public void testSortHistogram() {

        TimeSeriesAverager averager = new TimeSeriesAverager(FXCollections.observableArrayList(new TimeSeries(1, new double[3])));

        averager.numBins = 3;
        int numBins = averager.numBins;

        averager.indices = new ArrayList<>(numBins * numBins);
        for (int i = 0; i < numBins * numBins; i++) averager.indices.add(i);


        short[][] histogram = new short[][]{
                new short[]{3, 8, 2},
                new short[]{4, 8, 7},
                new short[]{0, 0, 1}
        };
        System.out.println(String.format("Arrays.toString(averager.sortHistogram(histogram)): %s", Arrays.toString(averager.sortHistogram(histogram))));

    }

    /**
     * Test that casting an integer to short is reverse compatible with the conversion between unsigned shorts and integers.
     */
    @Test public void testIntToShortCast(){

        int intValue = Short.MAX_VALUE + 10;
        short casted = (short) intValue;                    // casted value is negative
        assert(Short.toUnsignedInt(casted) == intValue);    // using toUnsignedInt restores the original value.

    }

}