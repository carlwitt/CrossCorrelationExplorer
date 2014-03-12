/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/

package Data;

import Data.FileModel;
import Data.LineParser;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.concurrent.WorkerStateEvent;
import javafx.embed.swing.JFXPanel;
import javafx.event.EventHandler;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Carl Witt
 */
public class FileModelTest {
    
    public String[] files = new String[]{
        "./data/inputData.txt",
        "./data/inputDataVariableLength.txt",
        "./data/lianhua_modified_excerpt.txt",
    };
    
    public LineParser[] separators = new LineParser[]{
        new LineParser(16),
        new LineParser(";"),
        new LineParser(16),
    };
    
    ExecutorService taskExecutor;
    
    public FileModelTest() {
        new JFXPanel();
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
        taskExecutor = Executors.newFixedThreadPool(4);
    }
    
    @After
    public void tearDown() {
    }
    
    /**
     * Test of getTimeSeriesLength method, of class FileModel.
     */
    @Test
    public void testGetTimeSeriesLength() {
        
        System.out.println("getTimeSeriesLength");
        
        final int expectedLength[] = new int[]{3,3,10};
        
        for (int i = 0; i < 3; i++) {
            final FileModel model = new FileModel(files[i], separators[i]);
            final int _expectedLength = expectedLength[i];
            
            model.getLoadFileService().setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override public void handle(WorkerStateEvent t) {
                    int result = model.getTimeSeriesLength();
                    assertEquals(_expectedLength, result);
                }
            });
            model.getLoadFileService().start();
        }
    }
    
    /**
     * Test of getNumberOfTimeSeries method, of class FileModel.
     */
    @Test
    public void testGetNumberOfTimeSeries() {
        System.out.println("getNumberOfTimeSeries");
        
        final int expectedNumber[] = new int[]{1,2,3};
        
        for (int i = 0; i < 3; i++) {
            final FileModel model = new FileModel(files[i], separators[i]);
            final int _expectedLength = expectedNumber[i];
            
            model.getLoadFileService().setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override public void handle(WorkerStateEvent t) {
                    int result = model.getNumberOfTimeSeries();
                    assertEquals(_expectedLength, result);
                }
            });
            model.getLoadFileService().start();
        }
    }
    
    /**
     * Test of splitToDouble method, of class FileModel.
     */
    @Test
    public void testSplitFixedColumnWidth() {
        System.out.println("splitFixedColumnWidth");
        String line = "  -4.9000000e+01             NaN";
        LineParser instance = new LineParser(16);
        double[] expResult = new double[]{-49., Double.NaN};
        double[] result = instance.splitToDouble(line);
        assertArrayEquals(expResult, result, 1e-10);
    }
    
    /**
     * Test of splitToDouble method, of class FileModel.
     */
    @Test
    public void testSplitCharacterSeparator() {
        System.out.println("splitCharacterSeparator");
        String line = "  -4.9000000e+01 ; -2 ; 0 ; 0.0 ; 2.e6 ; NaN";
        double[] expResult = new double[]{-49., -2., 0. , 0. , 2000000. ,  Double.NaN};
        
        LineParser instance = new LineParser(";");
        double[] result = instance.splitToDouble(line);
        
        assertArrayEquals(expResult, result,1e-10);
    }
    
    /**
     * Test of mapDataFile method, of class FileModel.
     */
    @Test
    public void testMapDataFile() throws IOException {
        System.out.println("mapDataFile");
        
        double[] xValues = new double[]{-5.0000000e+01, -4.9000000e+01, -4.8000000e+01, -4.7000000e+01, -4.6000000e+01, -4.5000000e+01, -4.4000000e+01, -4.3000000e+01, -4.2000000e+01, -4.1000000e+01};
        double[] y1Values = new double[]{-4.1200406e+00, -4.1785084e+00, -4.2291450e+00, -4.2797816e+00, -4.3304181e+00, -4.3810547e+00, -4.4296367e+00, -4.4187600e+00, -4.4078834e+00, -4.3970067e+00};
        double[] y2Values = new double[]{-3.8032569e+00, -3.8201816e+00, -3.8322239e+00, -3.8442663e+00, -3.8563086e+00, -3.8450658e+00, -3.8235302e+00, -3.8019947e+00, -3.7804591e+00, -3.8164684e+00};
        double[] y3Values = new double[]{-2.8348698e+00, -2.8154151e+00, -2.7959605e+00, -2.7765058e+00, -2.8245105e+00, -2.9064086e+00, -2.9883067e+00, -2.0702048e+00, -2.1515449e+00, -2.2117134e+00};
        double[][] columns = new double[][]{xValues, y1Values, y2Values, y3Values};
        
        final double[][] expectedRowValues = new double[10][4];
        for (int rows = 0; rows < 10; rows++) {
            for (int cols = 0; cols < 4; cols++) {
                expectedRowValues[rows][cols] = columns[cols][rows];
            }
        }
        
        final FileModel model = new FileModel(files[2], separators[2]);
        FileModel.LoadFileService service = model.getLoadFileService();
        service.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent t) {
                for (int rows = 0; rows < 10; rows++) {
//                    System.out.println(String.format("exp: %s\nact: %s", Arrays.toString(expectedRowValues[rows]),Arrays.toString(model.rowValues[rows])));
                    assertArrayEquals(expectedRowValues[rows], model.rowValues[rows], 1e-10);
                }
            }
        });
        service.start();
        
//        TimeSeries expected1 = new TimeSeries(ComplexSequence.create(xValues, y1Values));
//        TimeSeries expected2 = new TimeSeries(ComplexSequence.create(xValues, y2Values));
//        TimeSeries expected3 = new TimeSeries(ComplexSequence.create(xValues, y3Values));
//        assertEquals(dataModel.getContainerById(1).getDataItems(), expected1.getDataItems());
//        assertEquals(dataModel.getContainerById(2).getDataItems(), expected2.getDataItems());
//        assertEquals(dataModel.getContainerById(3).getDataItems(), expected3.getDataItems());
    }
    
    
}
