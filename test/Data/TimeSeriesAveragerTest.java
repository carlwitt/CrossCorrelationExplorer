package Data;

import Data.IO.FileModel;
import Data.IO.LineParser;
import javafx.collections.FXCollections;
import org.apache.commons.lang.ArrayUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class TimeSeriesAveragerTest {

    @Test public void testComputeAntiCorrelated() throws Exception{
        FileModel fileModel = new FileModel("data/small/inputDataAntiCorrelated.txt", new LineParser(16));
        fileModel.execute();
        double[] xValues = fileModel.getXValues();
        double[] yValues1 = fileModel.getYValues(1);
        double[] yValues2 = fileModel.getYValues(2);
        double[] yValues3 = fileModel.getYValues(3);

        TimeSeries ts1 = new TimeSeries(1, xValues, yValues1);
        TimeSeries ts2 = new TimeSeries(2, xValues, yValues2);
        TimeSeries ts3 = new TimeSeries(3, xValues, yValues3);

        TimeSeriesAverager timeSeriesAverager = new TimeSeriesAverager(Arrays.asList(ts1, ts2, ts3));
        timeSeriesAverager.setGroupSize(1);
        timeSeriesAverager.binSize = 1;
        timeSeriesAverager.getXValues();

        double[] expectedMinValues = new double[]{1, 0, 1, 0, 1, 0, 1, 1};
        double[] expectedMaxValues = new double[]{4, 3, 3, 4, 4, 3, 3, 4};
        short[][][] expectedHistograms = new short[][][] { {
                {0, 0, 1, 0},
                {0, 0, 0, 0},
                {0, 0, 0, 0},
                {0, 0, 0, 1}
        }, {
                {1, 0, 0},
                {0, 0, 0},
                {0, 0, 1},
                {0, 1, 0}
        }, {
                {1, 0, 0, 0, 0},
                {0, 1, 0, 0, 0},
                {0, 0, 0, 0, 1}
        }, {
                {0, 0, 0, 0},
                {1, 0, 0, 0},
                {0, 0, 0, 0},
                {0, 0, 0, 0},
                {0, 0, 0, 1}
        }, {
                {0, 0, 1, 0},
                {0, 0, 0, 0},
                {0, 0, 0, 0},
                {0, 0, 0, 1}
        }, {
                {1, 0, 0},
                {0, 0, 0},
                {0, 0, 1},
                {0, 1, 0}
        }, {
                {0, 0, 0, 0},
                {1, 0, 0, 0},
                {0, 0, 0, 1}
        }
        };

        // print histograms
//        Arrays.stream(timeSeriesAverager.histograms).forEach(histogram -> {
//            for (short[] row : histogram) {
//                for (int col = 0; col < histogram[0].length; col++) System.out.print(row[col] + " ");
//                System.out.println();
//            }
//            System.out.println();
//        });

        assertArrayEquals(expectedMinValues, timeSeriesAverager.lowestBinStartsAt, 1e-10);
        assertArrayEquals(expectedMinValues, timeSeriesAverager.minValues, 1e-10);
        assertArrayEquals(expectedMaxValues, timeSeriesAverager.maxValues, 1e-10);
        assertTrue(Arrays.deepEquals(expectedHistograms, timeSeriesAverager.histograms));
    }

    @Test public void testComputeSOP() {
        double[] xValues = new double[]{1, 2, 3, 4, 5, 6, 7, 8};
        double[] yValues1 = new double[]{0, 2, 2, 2, 2, 1, 0, 1};
        double[] yValues2 = new double[]{2, 0, 0, 2, 2, Double.NaN, 1, 2};

        // c  a  a  *  * !c     c
        //                a  c  a
        // a  c  c           a

        TimeSeries ts1 = new TimeSeries(1, xValues, yValues1);
        TimeSeries ts2 = new TimeSeries(2, xValues, yValues2);

        TimeSeriesAverager timeSeriesAverager = new TimeSeriesAverager(Arrays.asList(ts1, ts2));
        timeSeriesAverager.setGroupSize(1);
        timeSeriesAverager.binSize = 1;
        timeSeriesAverager.getXValues();

//        System.out.println(String.format("yVals1: %s", Arrays.toString(Arrays.stream(timeSeriesAverager.getYValues(1)).map(d -> Precision.round(d, 3)).toArray())));
//        System.out.println(String.format("yVals2: %s", Arrays.toString(Arrays.stream(timeSeriesAverager.getYValues(2)).map(d -> Precision.round(d, 3)).toArray())));

        double[] expectedMinValues = new double[]{0, 0, 0, 2, 2, 1, 0, 1};
        double[] expectedMaxValues = new double[]{2, 2, 2, 2, 2, 1, 1, 2};
        short[][][] expectedHistograms = new short[][][]{
            {
                {0, 1},
                {1, 0}
            },{
                {1, 0},
                {0, 1}
            },{
                {1, 0},
                {1, 0}
            },{
                {2, 0},
                {0, 0}
            },{
                {1, 0},
                {0, 0}
            },{
                {1, 0},
                {0, 0}
            },{
                {1, 0},
                {0, 1}
            }
        };

        // print histograms
        Arrays.stream(timeSeriesAverager.histograms).forEach(histogram -> {
            for (short[] row : histogram) {
                for (int col = 0; col < histogram[0].length; col++) System.out.print(row[col] + " ");
                System.out.println();
            }
            System.out.println();
        });

        assertTrue(Arrays.deepEquals(expectedHistograms, timeSeriesAverager.histograms));
        assertTrue(Arrays.equals(expectedMinValues, timeSeriesAverager.minValues));
        assertTrue(Arrays.equals(expectedMaxValues, timeSeriesAverager.maxValues));
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

        averager.binSize = 1;

        averager.indices = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) averager.indices.add(i);

        short[][] histogram = new short[][]{
            {3, 8, 2},
            {4, 8, 7},
            {0, 0, 1}
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

    @Test public void convertToShortArray(){
        List<Short> indices = Arrays.asList((short) 1, (short) 2, (short) 3, (short) 4, (short) 5);
        short[] vals = ArrayUtils.toPrimitive(indices.toArray(new Short[5]));
        System.out.println(String.format("vals: %s", vals));

    }

    @Test public void testFloor(){
        double number = -7.5;
        double binSize = 2.5;
        int binIdx = (int) Math.floor(number / binSize);
        assertTrue(binIdx == -3);
    }

}