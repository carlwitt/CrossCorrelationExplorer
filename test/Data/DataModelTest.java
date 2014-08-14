package Data;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DataModelTest {

    @Test public void testGetNumberOfTimeSeries() throws Exception {

        List<TimeSeries> tsA = TimeSeriesTest.randomTimeSeries(10, 123, 1l);
        List<TimeSeries> tsB = TimeSeriesTest.randomTimeSeries(25, 1234, 2l);

        DataModel dataModel = new DataModel(Arrays.asList(tsA, tsB));

        assertEquals(10, dataModel.getNumberOfTimeSeries(0));
        assertEquals(25, dataModel.getNumberOfTimeSeries(1));
        assertEquals(35, dataModel.getNumberOfTimeSeries());
    }
}