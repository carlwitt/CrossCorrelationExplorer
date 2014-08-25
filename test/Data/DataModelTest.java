package Data;

import Data.Correlation.CorrelationMatrix;
import Data.Windowing.WindowMetadata;
import org.junit.Test;

import java.io.IOException;
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

    @Test public void testMaxX() throws IOException {
        Experiment experiment = new Experiment("./data/sop/sop.nc");
        DataModel dataModel = experiment.dataModel;

        double step1 = dataModel.get(0,1).getDataItems().re[1] - dataModel.get(0,1).getDataItems().re[0];
        double step2 = dataModel.get(1,1).getDataItems().re[1] - dataModel.get(1,1).getDataItems().re[0];

        System.out.println(String.format("step1: %s", step1));
        System.out.println(String.format("step2: %s", step2));


        WindowMetadata metadata = new WindowMetadata.Builder(-1,1,200,1,70).tsA(dataModel.get(0,1)).tsB(dataModel.get(1,1)).pValue(0.05).build();
        CorrelationMatrix matrix = new CorrelationMatrix(metadata);

        System.out.println(String.format("metadata.numBaseWindows: %s", metadata.numBaseWindows));

        matrix.compute();
        System.out.println(String.format("matrix.getSize(): %s", matrix.getSize()));


    }
}