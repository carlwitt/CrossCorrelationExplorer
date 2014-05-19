/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package Visualization;

import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CorrelationMetadata;
import Data.Correlation.CrossCorrelation;
import Data.SharedData;
import Data.TimeSeries;
import javafx.embed.swing.JFXPanel;
import org.junit.*;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Carl Witt
 */
public class CorrelogramControllerTest {
    
    CorrelogramController instance;
    SharedData sharedData;
    
    public CorrelogramControllerTest() {
        
        // load JavaFX toolkit
        new JFXPanel();
        
        instance = new CorrelogramController();
        sharedData = new  SharedData();
        
        sharedData.dataModel.put(1, new TimeSeries(1, new double[]{1,2,3,4}, new double[]{1,1,1,1}));
        sharedData.dataModel.put(2, new TimeSeries(2, new double[]{1,2,3,4}, new double[]{1,2,3,4}));
        sharedData.dataModel.put(3, new TimeSeries(3, new double[]{1,2,3,4}, new double[]{1,4,1,8}));
        
        instance.setSharedData(sharedData);
        
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
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
        CorrelationMetadata metadata = new CorrelationMetadata(sharedData.correlationSetA, sharedData.correlationSetB, windowSize, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1);
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(metadata);
        correlationMatrix.compute();
        sharedData.setcorrelationMatrix(correlationMatrix);
        
        // compute the value range sample
        int meanResolution = 3;
        int stdDevResolution = 3;
        
        CorrelationMatrix expResult = new CorrelationMatrix(null);
        expResult.append(expResult.new Column(new double[]{1,1,1}, new double[]{0,1,2}, 0, 0));
        expResult.append(expResult.new Column(new double[]{3.5,3.5,3.5}, new double[]{0,1,2}, 1, 0));
        expResult.append(expResult.new Column(new double[]{6,6,6}, new double[]{0,1,2}, 2, 0));
        
        CorrelationMatrix result = instance.legend.valueRangeSample(meanResolution, stdDevResolution);
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
        CorrelationMetadata metadata = new CorrelationMetadata(sharedData.correlationSetA, sharedData.correlationSetB, windowSize, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1);
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(metadata);
        correlationMatrix.compute();
        sharedData.setcorrelationMatrix(correlationMatrix);
        
        // compute the value range sample
        int meanResolution = 3;
        int stdDevResolution = 3;
        
        CorrelationMatrix expResult = new CorrelationMatrix(null);
        expResult.append(expResult.new Column(new double[]{1}, new double[]{0}, 1, 0));

        CorrelationMatrix result = instance.legend.valueRangeSample(meanResolution, stdDevResolution);
        assertEquals(expResult, result);
    }
    
}
