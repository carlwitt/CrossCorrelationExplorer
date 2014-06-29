package Visualization;

import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CrossCorrelation;
import Data.SharedData;
import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import javafx.embed.swing.JFXPanel;
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

        sharedData.dataModel.put(0, 1, new TimeSeries(1,1,1,1));
        sharedData.dataModel.put(0, 2, new TimeSeries(1,2,3,4));
        sharedData.dataModel.put(0, 3, new TimeSeries(1,4,1,8));

        instance.setSharedData(sharedData);

    }
    @Test
    public void testValueRangeSample() {
        System.out.println("valueRangeSample");

        // compute a correlation matrix
        sharedData.dataModel.correlationSetA.clear();
        sharedData.dataModel.correlationSetB.clear();
        sharedData.dataModel.correlationSetA.add(sharedData.dataModel.get(1));
        sharedData.dataModel.correlationSetA.add(sharedData.dataModel.get(2));
        sharedData.dataModel.correlationSetA.add(sharedData.dataModel.get(3));
        sharedData.dataModel.correlationSetB.add(sharedData.dataModel.get(2));
        sharedData.dataModel.correlationSetB.add(sharedData.dataModel.get(3));

        int windowSize = 2;
        WindowMetadata metadata = new WindowMetadata(sharedData.dataModel.correlationSetA, sharedData.dataModel.correlationSetB, windowSize, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1);
        CorrelationMatrix.setSignificanceLevel(metadata, 0.05);
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(metadata);
        correlationMatrix.compute();
//        System.out.println(String.format("correlationMatrix: %s", correlationMatrix));

        // compute the value range sample
        int meanResolution = 3;
        int stdDevResolution = 3;

        instance.sourceStatistic[CorrelogramLegend.VERTICAL] = CorrelationMatrix.MEDIAN;
        instance.sourceStatistic[CorrelogramLegend.HORIZONTAL] = CorrelationMatrix.ABSOLUTE_SIGNIFICANT;

        double[][][] result = instance.valueRangeSample(correlationMatrix, meanResolution, stdDevResolution);
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
        sharedData.dataModel.correlationSetA.clear();
        sharedData.dataModel.correlationSetB.clear();
        sharedData.dataModel.correlationSetA.add(sharedData.dataModel.get(3));
        sharedData.dataModel.correlationSetB.add(sharedData.dataModel.get(3));

        int windowSize = 2;
        WindowMetadata metadata = new WindowMetadata(sharedData.dataModel.correlationSetA, sharedData.dataModel.correlationSetB, windowSize, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1);
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(metadata);
        correlationMatrix.compute();

//        System.out.println(String.format("correlationMatrix: %s", correlationMatrix));

        // compute the value range sample
        int meanResolution = 3;
        int stdDevResolution = 3;

        instance.sourceStatistic[CorrelogramLegend.VERTICAL] = CorrelationMatrix.STD_DEV;
        instance.sourceStatistic[CorrelogramLegend.HORIZONTAL] = CorrelationMatrix.MEAN;

        double[][][] result = instance.valueRangeSample(correlationMatrix, meanResolution, stdDevResolution);
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
        sharedData.dataModel.correlationSetA.clear();
        sharedData.dataModel.correlationSetB.clear();
        sharedData.dataModel.correlationSetA.add(sharedData.dataModel.get(3));
        sharedData.dataModel.correlationSetB.add(sharedData.dataModel.get(3));

        int windowSize = 2;
        WindowMetadata metadata = new WindowMetadata(sharedData.dataModel.correlationSetA, sharedData.dataModel.correlationSetB, windowSize, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1);
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(metadata);
        correlationMatrix.compute();

        System.out.println(String.format("correlationMatrix: %s", correlationMatrix));

        // compute the value range sample
        int meanResolution = 3;
        int stdDevResolution = 3;

        instance.sourceStatistic[CorrelogramLegend.VERTICAL] = null;
        instance.sourceStatistic[CorrelogramLegend.HORIZONTAL] = CorrelationMatrix.ABSOLUTE_SIGNIFICANT;

        double[][][] result = instance.valueRangeSample(correlationMatrix, meanResolution, stdDevResolution);
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

}