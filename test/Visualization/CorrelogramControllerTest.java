/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package Visualization;

import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CorrelogramMetadata;
import Data.Correlation.CrossCorrelation;
import Data.Correlation.DFT;
import Data.SharedData;
import Data.TimeSeries;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;

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
        CorrelogramMetadata metadata = new CorrelogramMetadata(sharedData.correlationSetA, sharedData.correlationSetB, windowSize, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED);
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(metadata);
        correlationMatrix.compute();
        sharedData.setcorrelationMatrix(correlationMatrix);
        
        // compute the value range sample
        int meanResolution = 3;
        int stdDevResolution = 3;
        
        CorrelationMatrix expResult = new CorrelationMatrix(null);
        expResult.append(new CorrelationMatrix.Column(new double[]{1,1,1}, new double[]{0,1,2}, 1));
        expResult.append(new CorrelationMatrix.Column(new double[]{3.5,3.5,3.5}, new double[]{0,1,2}, 3.5));
        expResult.append(new CorrelationMatrix.Column(new double[]{6,6,6}, new double[]{0,1,2}, 6));
        
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
        CorrelogramMetadata metadata = new CorrelogramMetadata(sharedData.correlationSetA, sharedData.correlationSetB, windowSize, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED);
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(metadata);
        correlationMatrix.compute();
        sharedData.setcorrelationMatrix(correlationMatrix);
        
        // compute the value range sample
        int meanResolution = 3;
        int stdDevResolution = 3;
        
        CorrelationMatrix expResult = new CorrelationMatrix(null);
        expResult.append(new CorrelationMatrix.Column(new double[]{1}, new double[]{0}, 1));

        CorrelationMatrix result = instance.legend.valueRangeSample(meanResolution, stdDevResolution);
        assertEquals(expResult, result);
    }
    
}
