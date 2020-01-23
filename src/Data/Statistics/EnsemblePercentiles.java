package Data.Statistics;

import Data.DataModel;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.Arrays;

/**
 * TODO: check on deprecation. is probably covered by the histogram time series chart in a better way.
 * Created by macbookdata on 10.10.14.
 */
public class EnsemblePercentiles {

    /** First dimension refers to ensemble ID,
     *  Second to data point ID,
     *  Third dimension refers to the percentile ID (referring to the percentile defined by the {@link #samplePercentilesAt} array)
     */
    public double[][][] percentiles;

    /** The percentiles being sampled at each data point ID (common x value), e.g. 0th, 25th, 50th, 75th and 100th percentile (samplePercentilesAt = {0.,25.,50.,75.,100.}). */
    double[] samplePercentilesAt;

    final int percentileSamples = 100; // the full range between the 1th and the 100th percentile is divided into this many evenly spaced samples.

    public EnsemblePercentiles(DataModel dataModel){

        DescriptiveStatistics ds = new DescriptiveStatistics();

        samplePercentilesAt = new double[percentileSamples];
        double percentileStep = 99./(percentileSamples-1); // e.g. two samples will give 1st and 100th percentile.
        for (int i = 0; i < percentileSamples; i++) samplePercentilesAt[i] = 1 + i * percentileStep;

        int numberOfEnsembles = dataModel.getNumberOfEnsembles();

        percentiles = new double[numberOfEnsembles][][];

        for (int ensembleID = 0; ensembleID < numberOfEnsembles; ensembleID++) {

            int numPoints = dataModel.getTimeSeriesLength(ensembleID);

            // compute median time series as probe series
            percentiles[ensembleID] = new double[numPoints][percentileSamples];

            // for each time point
            for (int i = 0; i < numPoints; i++) {

                // for each time series
                for (int j = 1; j <= dataModel.getNumberOfTimeSeries(ensembleID); j++) {
                    double yVal = dataModel.get(ensembleID, j).getDataItems().im[i];
                    if( ! Double.isNaN(yVal)) ds.addValue(yVal);
                }

                for (int pS = 0; pS  < percentileSamples; pS ++) {
                    percentiles[ensembleID][i][pS] = ds.getPercentile(samplePercentilesAt[pS]);
                }
                ds.clear();
            }

        }


    }

    public void print() {
        for (int i = 0; i < percentiles.length; i++) {
            System.out.println(String.format("ensemble ID: %s", i));
            for (int k = 0; k < percentileSamples; k++) {
                for (int j = 0; j < percentiles[i].length; j++) {
                    System.out.print(String.format("%.2f\t", percentiles[i][j][k]));
                }
                System.out.println();
            }
        }
    }
}
