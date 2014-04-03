/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package Visualization;

import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CorrelogramMetadata;
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
 * @author macbookdata
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
        CorrelogramMetadata metadata = new CorrelogramMetadata(sharedData.correlationSetA, sharedData.correlationSetB, windowSize, DFT.NA_ACTION.LEAVE_UNCHANGED);
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(metadata);
        correlationMatrix.compute();
        sharedData.setcorrelationMatrix(correlationMatrix);
        
        // compute the value range sample
        int meanResolution = 3;
        int stdDevResolution = 3;
        
        CorrelationMatrix expResult = new CorrelationMatrix(null);
        expResult.append(new CorrelationMatrix.Column(new double[]{1,1,1}, new double[]{2,1,0}, 1));
        expResult.append(new CorrelationMatrix.Column(new double[]{3.5,3.5,3.5}, new double[]{2,1,0}, 2));
        expResult.append(new CorrelationMatrix.Column(new double[]{6,6,6}, new double[]{2,1,0}, 3));
        
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
        CorrelogramMetadata metadata = new CorrelogramMetadata(sharedData.correlationSetA, sharedData.correlationSetB, windowSize, DFT.NA_ACTION.LEAVE_UNCHANGED);
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(metadata);
        correlationMatrix.compute();
        sharedData.setcorrelationMatrix(correlationMatrix);
        
        // compute the value range sample
        int meanResolution = 3;
        int stdDevResolution = 3;
        
        CorrelationMatrix expResult = new CorrelationMatrix(null);
        expResult.append(new CorrelationMatrix.Column(new double[]{1,1,1}, new double[]{0,0,0}, 1));
        expResult.append(new CorrelationMatrix.Column(new double[]{1,1,1}, new double[]{0,0,0}, 2));
        expResult.append(new CorrelationMatrix.Column(new double[]{1,1,1}, new double[]{0,0,0}, 3));
        
        CorrelationMatrix result = instance.valueRangeSample(meanResolution, stdDevResolution);
        assertEquals(expResult, result);
    }
    
    @Test @Ignore
    public void testResetView() {
        System.out.println("resetView");
        ActionEvent e = null;
        CorrelogramController instance = new CorrelogramController();
        instance.resetView(e);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }
    
    @Test @Ignore
    public void testUpdateLegend() {
        System.out.println("updateLegend");
        CorrelogramController instance = new CorrelogramController();
        instance.updateLegend();
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }
    
}
