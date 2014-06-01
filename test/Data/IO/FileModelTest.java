/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/

package Data.IO;

import Data.TimeSeries;
import javafx.concurrent.WorkerStateEvent;
import javafx.embed.swing.JFXPanel;
import javafx.event.EventHandler;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

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

    // initializes the java fx framework (because the test uses services)
    public FileModelTest() {
        new JFXPanel();
    }

    /**
     * Test of getTimeSeriesLength method, of class FileModel.
     */
    @Test
    public void testGetTimeSeriesLength() {
        
        System.out.println("getTimeSeriesLength");
        
        final int expectedLength[] = new int[]{12,3,10};
        
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
        
        final int expectedNumberTimeSeries[] = new int[]{2,2,3};
        
        for (int i = 0; i < 3; i++) {
            final FileModel model = new FileModel(files[i], separators[i]);
            final int _expectedLength = expectedNumberTimeSeries[i];

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

    @Test @Ignore
    public void testPersist() throws IOException {

        double[] xValues = new double[]{-1,0,1,2,3,4};
        List<TimeSeries> ts = Arrays.asList(
                new TimeSeries(1, xValues, new double[]{1,2,3,4,5,6}),
                new TimeSeries(1, xValues, new double[]{1e1,1e2,1e3,1e4,1e5,1e6}),
                new TimeSeries(1, xValues, new double[]{1e-1,1e-2,1e-3,1e-4,1e-5,1e-6})
        );
        // save all time series
        FileModel.persist(ts, "./data/persistTestOutput.txt");

    }

    /**
     * Test parallel processing of array partitions, as can be used for parsing.
     * @throws Exception
     */
    @Test public void threadJoin() throws Exception{

        int numThreads = Runtime.getRuntime().availableProcessors();
        System.out.println("Available Processors: "+numThreads);


        int numLines = 1112894;
        final String[] lines = new String[numLines];
        for (int i = 0; i < numLines; i++) {
            lines[i] = ""+i;
        }

        final int[] results = new int[numLines];
        final int[] expectedResults = new int[numLines];
        for (int i = 0; i < numLines; i++) {
            expectedResults[i] = -i;
        }

        Thread[] processors = new Thread[numThreads];

        long before = System.currentTimeMillis();

        int step = numLines/numThreads;
        for (int i = 0; i < numThreads; i++) {
            final int from = i * step, to = i == numThreads-1 ? numLines-1 : (i+1)*step-1;
            final int idx = i;
            processors[i] = new Thread(new Runnable() {
                int fromIndex = from, toIndex = to;
                int threadIndex = idx;
                @Override
                public void run() {
                    System.out.println(String.format("Starting thread %s, [%s, %s]", threadIndex, fromIndex, toIndex));
                    for (int j = fromIndex; j <= toIndex; j++) {
                        int val = -1 * Integer.parseInt(lines[j]);
                        results[j] = val;
//                        System.out.println(threadIndex+": " + val);
                    }
                }
            });
            processors[i].start();
        }

        for (int i = 0; i < numThreads; i++) {
            processors[i].join();
        }

        System.out.println("needed time: "+(System.currentTimeMillis()-before));

        assertArrayEquals(expectedResults, results);

    }

    /**
     * Test that the parsed content from a persisted file equals the parsed contents of some original file.
     * Extremely difficult to get the thread synchronization done.
     * - invoke a wait after each service start to avoid that the test routine is exited before the thread is done
     * - use synchronized to be allowed to call service.wait() at all
     * - use synchronized to be allowed to call service.notify()
     *
     * However, gets caught in an endless loop.
     *
     * @throws IOException
     */
    @Test @Ignore
    public void testPersistAsynchronous() throws IOException {

        // load all time series from a file
        final FileModel model = new FileModel(files[2], separators[2]);
        final FileModel.LoadFileService service = model.getLoadFileService();
        service.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent t) {

                // create time series from file data
                final ArrayList<TimeSeries> timeSeriesInFile = new ArrayList<TimeSeries>(model.getNumberOfTimeSeries());

                for (int j = 0; j < model.getNumberOfTimeSeries(); j++) {
                    TimeSeries timeSeries = new TimeSeries(j, model.getXValues(j), model.getYValues(j));
                    timeSeriesInFile.add(timeSeries);
                    System.out.println("Read time series: " + timeSeries);
                }

                // persist the data...
                String outPath = "./data/persistTestOutput.txt";
                try {
                    model.persist(timeSeriesInFile, outPath);
                } catch (IOException e) {
                    System.out.println("Couldn't persist.");
                    e.printStackTrace();
                }

                System.out.println("Persisted everything.");

                // ... and read it again
                final FileModel modelForPersisted = new FileModel(outPath, new LineParser(16));
                final FileModel.LoadFileService serviceForPersisted = modelForPersisted.getLoadFileService();
                serviceForPersisted.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
                    @Override
                    public void handle(WorkerStateEvent event) {

                        System.out.println("Reading persisted file content.");

                        // and compare to the initial parsed ones
                        for (int j = 0; j < modelForPersisted.getNumberOfTimeSeries(); j++) {
                            TimeSeries timeSeries = new TimeSeries(j, modelForPersisted.getXValues(j), modelForPersisted.getYValues(j));
                            assertArrayEquals(timeSeriesInFile.get(j).getDataItems().re, timeSeries.getDataItems().re, 1e-15);
                            assertArrayEquals(timeSeriesInFile.get(j).getDataItems().im, timeSeries.getDataItems().im, 1e-15);
                        }
                        synchronized (serviceForPersisted){
                            serviceForPersisted.notify();
                        }

                    }
                });
                synchronized (serviceForPersisted){
                    serviceForPersisted.start();
                    try { serviceForPersisted.wait(); } catch (Exception e) { e.printStackTrace(); }
                }

                synchronized (service){
                    service.notify();
                }

            }
        });


        synchronized (service){
            service.start();
//            while(service.getState() != Worker.State.SUCCEEDED);
            try { service.wait(); } catch (Exception e) { e.printStackTrace(); }
        }

    }
    
    
}
