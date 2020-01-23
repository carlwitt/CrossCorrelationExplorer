package Data;

import Data.Correlation.CorrelationMatrix;
import Data.IO.FileModel;
import Data.Windowing.WindowMetadata;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DataModelTest {

    @Test
    public void testEnsembleClipping() throws Exception {

        // a negative delta x input
        double[][] xValues = new double[][]{
                {2,1,0},
                {4,3,2,1},
        };
        int[][] expectedClipping = new int[][]{
                {0,1},
                {2,3},
        };
        assertTrue(Arrays.deepEquals(expectedClipping, DataModel.findEnsembleClippings(xValues).get()));

        // a completely contained ensemble
        xValues = new double[][]{
                {-1.5, 0, 1.5, 3, 4.5},
                {1.5,3},
        };
        expectedClipping = new int[][]{
                {2,3},
                {0,1},
        };
        assertTrue(Arrays.deepEquals(expectedClipping, DataModel.findEnsembleClippings(xValues).get()));

        // two disjoint ensembles
        xValues = new double[][]{
                {-1.5, 0, 1.5, 3, 4.5},
                {6,7.5},
        };
        assertFalse(DataModel.findEnsembleClippings(xValues).isPresent());

        // two disjoint ensembles, with negative delta x.
        xValues = new double[][]{
                {1.5, 0, -1.5, -3, -4.5},
                {-6,-7.5},
        };
        assertFalse(DataModel.findEnsembleClippings(xValues).isPresent());
    }

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

    @Test public void compute_mean_median_time_series() throws Exception {

        DescriptiveStatistics ds = new DescriptiveStatistics();

        // sanbao heshang time series
        Experiment experiment = new Experiment("./data/eurovis_talk/sanbao_heshang.nc");
        DataModel dataModel = experiment.dataModel;

        for (int ensemble_id = 0; ensemble_id < 2; ensemble_id++) {

            final int numberOfTimeSeries = dataModel.getNumberOfTimeSeries(ensemble_id);

            int ts_length = dataModel.get(ensemble_id,1).getSize();
            double[] mean_ts = new double[ts_length];
            double[] median_ts = new double[ts_length];

            for (int t = 0; t < ts_length; t++) {

                // clear for each new time step
                ds.clear();

                // add current time point of each time series to the statistics
                for(TimeSeries ts : dataModel.getEnsemble(ensemble_id).values()){
                    ds.addValue(ts.getDataItems().im[t]);
                }

                mean_ts[t] = ds.getMean();
                median_ts[t] = ds.getPercentile(50.);
            }

            final double[] x_values = dataModel.get(ensemble_id, 1).getDataItems().re;
            FileModel.persist(Arrays.asList(new TimeSeries(0, x_values, mean_ts)), String.format("sanbao_heshang_ensemble%s_%sts_mean.txt",ensemble_id+1, numberOfTimeSeries));
            FileModel.persist(Arrays.asList(new TimeSeries(0, x_values, median_ts)), String.format("sanbao_heshang_ensemble%s_%sts_median.txt",ensemble_id+1, numberOfTimeSeries));

        }


        // ERP complete time series
        experiment = new Experiment("./data/eurovis_talk/ERP_complete.nc");
        dataModel = experiment.dataModel;

        for (int ensemble_id = 0; ensemble_id < 2; ensemble_id++) {

            final int numberOfTimeSeries = dataModel.getNumberOfTimeSeries(ensemble_id);

            int ts_length = dataModel.get(ensemble_id,1).getSize();
            double[] mean_ts = new double[ts_length];
            double[] median_ts = new double[ts_length];

            for (int t = 0; t < ts_length; t++) {

                // clear for each new time step
                ds.clear();

                // add current time point of each time series to the statistics
                for(TimeSeries ts : dataModel.getEnsemble(ensemble_id).values()){
                    ds.addValue(ts.getDataItems().im[t]);
                }

                mean_ts[t] = ds.getMean();
                median_ts[t] = ds.getPercentile(50.);
            }

            final double[] x_values = dataModel.get(ensemble_id, 1).getDataItems().re;
            FileModel.persist(Arrays.asList(new TimeSeries(0, x_values, mean_ts)), String.format("ERP_complete_ensemble%s_%sts_mean.txt",ensemble_id+1, numberOfTimeSeries));
            FileModel.persist(Arrays.asList(new TimeSeries(0, x_values, median_ts)), String.format("ERP_complete_ensemble%s_%sts_median.txt",ensemble_id+1, numberOfTimeSeries));

        }




    }

}