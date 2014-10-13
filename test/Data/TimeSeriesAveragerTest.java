package Data;

import javafx.collections.FXCollections;
import org.junit.Test;

import java.util.Arrays;

public class TimeSeriesAveragerTest {

    @Test
    public void testGetXValues() throws Exception {

    }

    @Test
    public void testGetYValues() throws Exception {

    }

    @Test
    public void testCompute() throws Exception {
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


}