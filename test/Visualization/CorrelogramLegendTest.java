package Visualization;

import Data.Correlation.CorrelationMatrix;
import Data.SharedData;
import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class CorrelogramLegendTest {

    CorrelogramLegend instance;
    SharedData sharedData;

    public CorrelogramLegendTest() {

        // load JavaFX toolkit
        new JFXPanel();

        instance = new CorrelogramLegend(new Correlogram(new MultiDimensionalPaintScale(1200,400)), new MultiDimensionalPaintScale(24,4));
        sharedData = new  SharedData();

        sharedData.experiment.dataModel.put(0, 1, new TimeSeries(1, 1,1,1,1));
        sharedData.experiment.dataModel.put(0, 2, new TimeSeries(1, 1,2,3,4));
        sharedData.experiment.dataModel.put(0, 3, new TimeSeries(1, 1,4,1,8));

        instance.setSharedData(sharedData);

    }
    @Test
    public void testValueRangeSample() {
        System.out.println("valueRangeSample");

        // compute a correlation matrix
        sharedData.experiment.dataModel.correlationSetA.clear();
        sharedData.experiment.dataModel.correlationSetB.clear();
        sharedData.experiment.dataModel.correlationSetA.add(sharedData.experiment.dataModel.get(0,1));
        sharedData.experiment.dataModel.correlationSetA.add(sharedData.experiment.dataModel.get(0,2));
        sharedData.experiment.dataModel.correlationSetA.add(sharedData.experiment.dataModel.get(0,3));
        sharedData.experiment.dataModel.correlationSetB.add(sharedData.experiment.dataModel.get(0,2));
        sharedData.experiment.dataModel.correlationSetB.add(sharedData.experiment.dataModel.get(0,3));

        int windowSize = 2;
        WindowMetadata metadata = new WindowMetadata(sharedData.experiment.dataModel.correlationSetA, sharedData.experiment.dataModel.correlationSetB, windowSize, -2, 2, 1, 1);
        CorrelationMatrix.setSignificanceLevel(metadata, 0.05);
        CorrelationMatrix CorrelationMatrix = new CorrelationMatrix(metadata);
        CorrelationMatrix.compute();
//        System.out.println(String.format("correlationMatrix: %s", correlationMatrix));

        // compute the value range sample
        int meanResolution = 3;
        int stdDevResolution = 3;

        instance.sourceStatistic[CorrelogramLegend.VERTICAL] = CorrelationMatrix.MEDIAN;
        instance.sourceStatistic[CorrelogramLegend.HORIZONTAL] = CorrelationMatrix.ABSOLUTE_SIGNIFICANT;

        double[][][] result = instance.valueRangeSample(CorrelationMatrix, meanResolution, stdDevResolution);
        for (int row = 0; row < result.length; row++) {
            for (int col = 0; col < result[0].length; col++) {
//                assertArrayEquals(expectedResult[row][col], result[row][col], 1e-15);
                System.out.print(String.format("(%s, %s)", result[row][col][CorrelogramLegend.HORIZONTAL], result[row][col][CorrelogramLegend.VERTICAL]));
            }
            System.out.println();
        }
//        assertEquals(expResult, result);
    }

    /**
     * Tests the edge case where mean and standard deviation are constant, so the range will be zero
     */
    @Test
    public void testValueRangeSampleEdgeCase() {
        System.out.println("valueRangeSample edge case");

        // compute a correlation matrix
        sharedData.experiment.dataModel.correlationSetA.clear();
        sharedData.experiment.dataModel.correlationSetB.clear();
        sharedData.experiment.dataModel.correlationSetA.add(sharedData.experiment.dataModel.get(0,3));
        sharedData.experiment.dataModel.correlationSetB.add(sharedData.experiment.dataModel.get(0,3));

        int windowSize = 2;
        WindowMetadata metadata = new WindowMetadata(sharedData.experiment.dataModel.correlationSetA, sharedData.experiment.dataModel.correlationSetB, windowSize, -2, 2, 1, 1);
        CorrelationMatrix CorrelationMatrix = new CorrelationMatrix(metadata);
        CorrelationMatrix.compute();

//        System.out.println(String.format("correlationMatrix: %s", correlationMatrix));

        // compute the value range sample
        int meanResolution = 3;
        int stdDevResolution = 3;

        instance.sourceStatistic[CorrelogramLegend.VERTICAL] = CorrelationMatrix.STD_DEV;
        instance.sourceStatistic[CorrelogramLegend.HORIZONTAL] = CorrelationMatrix.MEAN;

        double[][][] result = instance.valueRangeSample(CorrelationMatrix, meanResolution, stdDevResolution);
        double[][][] expectedResult = new double[1][3][2];
        expectedResult[0][0][CorrelogramLegend.HORIZONTAL] = -1;
        expectedResult[0][0][CorrelogramLegend.VERTICAL] = 0;
        expectedResult[0][1][CorrelogramLegend.HORIZONTAL] = 0;
        expectedResult[0][1][CorrelogramLegend.VERTICAL] = 0;
        expectedResult[0][2][CorrelogramLegend.HORIZONTAL] = 1;
        expectedResult[0][2][CorrelogramLegend.VERTICAL] = 0;
        for (int row = 0; row < result.length; row++) {
            for (int col = 0; col < result[0].length; col++) {
                assertArrayEquals(expectedResult[row][col], result[row][col], 1e-15);
//                System.out.print(String.format("(%s, %s)", result[row][col][CorrelogramLegend.HORIZONTAL], result[row][col][CorrelogramLegend.VERTICAL]));
            }
            System.out.println();
        }
//        assertEquals(expResult, result);
    }

    @Test
    public void testValueRangeOneDimensional() {
        System.out.println("valueRangeSample edge case");

        // compute a correlation matrix
        sharedData.experiment.dataModel.correlationSetA.clear();
        sharedData.experiment.dataModel.correlationSetB.clear();
        sharedData.experiment.dataModel.correlationSetA.add(sharedData.experiment.dataModel.get(0,3));
        sharedData.experiment.dataModel.correlationSetB.add(sharedData.experiment.dataModel.get(0,3));

        int windowSize = 2;
        WindowMetadata metadata = new WindowMetadata(sharedData.experiment.dataModel.correlationSetA, sharedData.experiment.dataModel.correlationSetB, windowSize, -2, 2, 1, 1);
        CorrelationMatrix CorrelationMatrix = new CorrelationMatrix(metadata);
        CorrelationMatrix.compute();

        System.out.println(String.format("correlationMatrix: %s", CorrelationMatrix));

        // compute the value range sample
        int meanResolution = 3;
        int stdDevResolution = 3;

        instance.sourceStatistic[CorrelogramLegend.VERTICAL] = null;
        instance.sourceStatistic[CorrelogramLegend.HORIZONTAL] = CorrelationMatrix.ABSOLUTE_SIGNIFICANT;

        double[][][] result = instance.valueRangeSample(CorrelationMatrix, meanResolution, stdDevResolution);
        double[][][] expectedResult = new double[1][1][2];
        expectedResult[0][0][CorrelogramLegend.HORIZONTAL] = 0;
        expectedResult[0][0][CorrelogramLegend.VERTICAL] = 0;
        for (int row = 0; row < result.length; row++) {
            for (int col = 0; col < result[0].length; col++) {
                System.out.print(String.format("(%s, %s)", result[row][col][CorrelogramLegend.HORIZONTAL], result[row][col][CorrelogramLegend.VERTICAL]));
//                assertArrayEquals(expectedResult[row][col], result[row][col], 1e-15);
            }
            System.out.println();
        }
//        assertEquals(expResult, result);
    }

    @Test public void boundsWithNaNComponents(){
        Bounds b = new BoundingBox(Double.NaN, Double.NaN - 10., 10. - Double.NaN, Double.NaN);
        assert Double.isNaN(b.getWidth());
        assert Double.isNaN(b.getHeight());
    }

}