package Visualization;

import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CrossCorrelation;
import Data.SharedData;
import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import javafx.embed.swing.JFXPanel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CorrelogramLegendTest {

    CorrelogramLegend instance;
    SharedData sharedData;

    public CorrelogramLegendTest() {

        // load JavaFX toolkit
        new JFXPanel();

        instance = new CorrelogramLegend(new MultiDimensionalPaintScale(24,4));
        sharedData = new  SharedData();

        sharedData.dataModel.put(1, new TimeSeries(1, new double[]{1,2,3,4}, new double[]{1,1,1,1}));
        sharedData.dataModel.put(2, new TimeSeries(2, new double[]{1,2,3,4}, new double[]{1,2,3,4}));
        sharedData.dataModel.put(3, new TimeSeries(3, new double[]{1,2,3,4}, new double[]{1,4,1,8}));

        instance.setSharedData(sharedData);

    }
    @Test
    public void testValueRangeSample() {
        System.out.println("valueRangeSample");

        // compute a correlation matrix
        sharedData.correlationSetA.clear();
        sharedData.correlationSetB.clear();
        sharedData.correlationSetA.add(sharedData.dataModel.get(1));
        sharedData.correlationSetB.add(sharedData.dataModel.get(2));
        sharedData.correlationSetB.add(sharedData.dataModel.get(3));

        int windowSize = 1;
        WindowMetadata metadata = new WindowMetadata(sharedData.correlationSetA, sharedData.correlationSetB, windowSize, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1);
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(metadata);
        correlationMatrix.compute();
        sharedData.setcorrelationMatrix(correlationMatrix);

        // compute the value range sample
        int meanResolution = 3;
        int stdDevResolution = 3;

        CorrelationMatrix expResult = new CorrelationMatrix(null);
        expResult.append(expResult.new CorrelationColumnBuilder(0, 0).mean(new double[]{1, 1, 1}).standardDeviation(new double[]{0, 1, 2}).build());
        expResult.append(expResult.new CorrelationColumnBuilder(1, 0).mean(new double[]{3.5, 3.5, 3.5}).standardDeviation(new double[]{0, 1, 2}).build());
        expResult.append(expResult.new CorrelationColumnBuilder(2, 0).mean(new double[]{6, 6, 6}).standardDeviation(new double[]{0, 1, 2}).build());

        CorrelationMatrix result = instance.valueRangeSample(meanResolution, stdDevResolution);
        assertEquals(expResult, result);
        System.out.println(result);
    }

    /**
     * Tests the edge case where mean and standard deviation are constant, so the range will be zero
     */
    @Test
    public void testValueRangeSampleEdgeCase() {
        System.out.println("valueRangeSample edge case");

        // compute a correlation matrix
        sharedData.correlationSetA.clear();
        sharedData.correlationSetB.clear();
        sharedData.correlationSetA.add(sharedData.dataModel.get(1));
        sharedData.correlationSetB.add(sharedData.dataModel.get(1));

        int windowSize = 1;
        WindowMetadata metadata = new WindowMetadata(sharedData.correlationSetA, sharedData.correlationSetB, windowSize, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1);
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(metadata);
        correlationMatrix.compute();
        sharedData.setcorrelationMatrix(correlationMatrix);

        // compute the value range sample
        int meanResolution = 3;
        int stdDevResolution = 3;

        CorrelationMatrix expResult = new CorrelationMatrix(null);
        expResult.append(expResult.new CorrelationColumnBuilder(1, 0).mean(new double[]{1}).standardDeviation(new double[]{0}).build());

        CorrelationMatrix result = instance.valueRangeSample(meanResolution, stdDevResolution);
        assertEquals(expResult, result);
    }

}