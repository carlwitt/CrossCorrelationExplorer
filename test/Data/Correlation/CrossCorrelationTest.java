package Data.Correlation;

import Data.TimeSeries;
import Data.TimeSeriesTest;
import Data.Windowing.WindowMetadata;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CrossCorrelationTest {

    // test the slow but simple cross correlation between two time series
    @Test public void testCrossCorrelationNaiveAtomic(){

        int windowSize = 4, baseWindowOffset = 2, taumin = -1, tauMax = 3, tauStep = 2;
        TimeSeries tsB = new TimeSeries(1, 1,2,3,4,4,3,2,1);
        TimeSeries tsA = new TimeSeries(1, 4,3,2,1,1,2,3,4);

        CorrelationMatrix exp = new CorrelationMatrix(null);
        CorrelationMatrix.CorrelationColumn[] expected = new CorrelationMatrix.CorrelationColumn[]{
                exp.new CorrelationColumnBuilder(0, -1).mean(new double[]{Double.NaN, Double.NaN, Double.NaN}).build(),
                exp.new CorrelationColumnBuilder(2, -1).mean(new double[]{-0.301511344577764, -0.301511344577764, Double.NaN}).build(),
                exp.new CorrelationColumnBuilder(4, -1).mean(new double[]{-0.943879807448539, -0.943879807448539, 0.943879807448539}).build(),
                exp.new CorrelationColumnBuilder(6, -1).mean(new double[]{Double.NaN, Double.NaN, Double.NaN}).build()
        };

        WindowMetadata metadata = CorrelationMatrix.setSignificanceLevel(new WindowMetadata(tsA, tsB, windowSize, taumin, tauMax, tauStep, baseWindowOffset), 0.05);
        CorrelationMatrix result = CrossCorrelation.naiveCrossCorrelation(metadata);

        System.out.println(String.format("result: %s", result));
        for (int i = 0; i < expected.length; i++) {
            assertArrayEquals(expected[i].data[CorrelationMatrix.MEAN], result.getColumn(i).data[CorrelationMatrix.MEAN], 1e-15);
            assertEquals(expected[i].tauMin, result.getColumn(i).tauMin);
            assertEquals(expected[i].windowStartIndex, result.getColumn(i).windowStartIndex);
        }
    }

    // test that the order of the input sets of the naive cc doesn't matter
    @Test public void testSymmetric(){

        int numTimeSeries = 20;
        int length = 60;

        List<TimeSeries> tsA = TimeSeriesTest.randomTimeSeries(numTimeSeries, length, 1l);
        List<TimeSeries> tsB = TimeSeriesTest.randomTimeSeries(numTimeSeries, length, 1l);

        int windowSize = 4, baseWindowOffset = 2, tauMin = -1, tauMax = 3, tauStep = 2;
        WindowMetadata metadata1 = new WindowMetadata(tsA, tsB, windowSize, tauMin, tauMax, tauStep, baseWindowOffset);
        CorrelationMatrix.setSignificanceLevel(metadata1, 0.05);
        WindowMetadata metadata2 = new WindowMetadata(tsB, tsA, windowSize, tauMin, tauMax, tauStep, baseWindowOffset);
        CorrelationMatrix.setSignificanceLevel(metadata2, 0.05);

        CorrelationMatrix matrix1 = CrossCorrelation.naiveCrossCorrelation(metadata1);
        CorrelationMatrix matrix2 = CrossCorrelation.naiveCrossCorrelation(metadata2);

        List<CorrelationMatrix.CorrelationColumn> columns1 = matrix1.columns;
        for (int i = 0; i < columns1.size(); i++) {
            assertEquals(matrix1.columns.get(i), matrix2.columns.get(i));
        }

    }
}