package Data;

import javafx.collections.FXCollections;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class TimeSeriesAveragerTest {

    @Test public void testGetXValues() throws Exception {

    }

    @Test public void testGetYValues() throws Exception {

    }

    @Test public void testCompute() throws Exception {
        TimeSeries ts1 = new TimeSeries(1, new double[]{2,3,4,5,6,7,8,9,10,11}, new double[]{2,2,3,3,4,4,5,5,6,6});
        TimeSeries ts2 = new TimeSeries(2, new double[]{2,3,4,5,6,7,8,9,10,11}, new double[]{1,2,3,4,5,6,7,8,9,10});

        TimeSeriesAverager timeSeriesAverager = new TimeSeriesAverager(FXCollections.observableArrayList(ts1, ts2));
        timeSeriesAverager.setBinSize(4);

        String xVals = Arrays.toString(timeSeriesAverager.getXValues());
        String yVals1 = Arrays.toString(timeSeriesAverager.getYValues(1));
        String yVals2 = Arrays.toString(timeSeriesAverager.getYValues(2));
        System.out.println(String.format("xVals: %s", xVals));
        System.out.println(String.format("yVals1: %s", yVals1));
        System.out.println(String.format("yVals2: %s", yVals2));
    }

    @Test public void testSortHistogram(){

        TimeSeriesAverager averager = new TimeSeriesAverager(FXCollections.observableArrayList(new TimeSeries(1, new double[3])));

        averager.numBins = 3;
        int numBins = averager.numBins;

        averager.indices = new ArrayList<>(numBins*numBins);
        for (int i = 0; i < numBins*numBins; i++) averager.indices.add(i);


        short[][] histogram = new short[][]{
            new short[]{3,8,2},
            new short[]{4,8,7},
            new short[]{0,0,1}
        };
        System.out.println(String.format("Arrays.toString(averager.sortHistogram(histogram)): %s", Arrays.toString(averager.sortHistogram(histogram))));;

    }

}