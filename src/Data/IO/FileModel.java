package Data.IO;

import Data.TimeSeries;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Abstracts from the way a number of time seriese is stored in a text file.
 * This specific model parses a number matrix from the string data.
 * It also encapsulates the conventions on how to interpret these numbers (i.e. how x- and y-values are stored within the matrix).
 *
 * Method overview:
 *   getXValues, getYValues
 *      retrieve the data
 *   getLoadFileService
 *      returns an object for asynchronous (and progress bar enabled) file parsing
 *   persist
 *      write data in the specified format (used for tests)
 *
 * @author Carl Witt
 */
public class FileModel {
    
    /** A matrix representing all numbers in the file. The first dimension refers to rows and the second to columns. */
    double[][] rowValues;
    private double[] firstColumn; // cached first column of the matrix
    
    private static final String DEFAULT_ENCODING = "UTF-8";

    private final StringProperty filename = new SimpleStringProperty();
    public final void setFilename(String value) { filename.set(value); }
    final String getFilename() { return filename.get(); }
    public final StringProperty filenameProperty() { return filename; }
        
    /** The separator to split up lines of text into pieces before parsing them. */
    private LineParser separator;

    /** Loads and parses a file asynchronously (doesn't block the UI).
     * Parsing a few hundred MB can take a few seconds. */
    public final LoadFileService loadFileService = new LoadFileService();
            
    public FileModel(String filename, LineParser lineParser){
        setFilename(filename);
        this.separator = lineParser;
    }
    
    // -------------------------------------------------------------------------
    // Time series access 
    // -------------------------------------------------------------------------
    
    /** All time series are assumed to have the same length. */
    public int getTimeSeriesLength(){
        return rowValues.length;
    }
    /** The time series are stored in columns, where the first column contains the x-values for all time series. */
    public int getNumberOfTimeSeries(){
        return rowValues[0].length - 1; // the first column contains the x values for all time series
    }

    /** 
     * @param index one-based
     * @return x values of the time series
     */
    public double[] getXValues(int index){
        return firstColumn;
    }
    /** 
     * @param index one-based
     * @return y values of the time series
     */
    public double[] getYValues(int index){
        double[] result = new double[getTimeSeriesLength()];
        for (int row = 0; row < result.length; row++) {
            result[row] = rowValues[row][index];
        }
        return result;
    }

    /**
     * Writes a set of time series in the specified format to disk.
     * @param timeSeries the time series (all of them must have the same x values.)
     * @param targetPath the path to the file where the results shall be written (will be overwritten)
     * @throws IOException if the output can't be written
     */
    public static void persist(List<TimeSeries> timeSeries, String targetPath) throws IOException {

        // all time series and one column for their common x values
        ArrayList<String> lines = new ArrayList<>(timeSeries.size()+1);

        TimeSeries representativeTimeSeries = timeSeries.get(0);
        for (int i = 0; i < timeSeries.get(0).getSize(); i++) {

//            lineValues.clear();
//            lineValues.add(data.firstEntry().getValue().getDataItems().re[i]);
            String line = "";

            line += String.format("%-16s", representativeTimeSeries.getDataItems().re[i]);

            for(TimeSeries ts : timeSeries){
                //lineValues.add(ts.getDataItems().get(i));
                line += String.format(Locale.ENGLISH, "%-16e", ts.getDataItems().im[i]);
            }

            lines.add(line);

        }

        FileUtils.writeLines(new File(targetPath), lines);

    }
    
    // -------------------------------------------------------------------------
    // set up and usage
    // -------------------------------------------------------------------------

    // define the input format of the file by specifying how to split lines into string representing numbers
    public void setSeparatorWidth(int fixedWidth){
        separator = new LineParser(fixedWidth);
    }
    public void setSeparatorCharacter(String separatorCharacter) {
        separator = new LineParser(separatorCharacter);
    }
    public LoadFileService getLoadFileService() {
        return loadFileService;
    }
    
    /** Reusable concurrent execution logic for loading and parsing files with progress reporting.
     * The service can be used to load and parse the file contents asynchronously (non-UI-blocking) and to display progress.  */
    public class LoadFileService extends Service<Void> {
                
        @Override protected Task<Void> createTask() {
            final String _filename = getFilename();
            return new Task<Void>() {
                
                private FileModel fail(String message){
                    System.err.println(message);
                    this.updateMessage(message);
                    return null;
                }
                
                @Override protected Void call() {
                    
                    // load file contents as list of lines
                    updateMessage("Loading File");
                    File file = new File(_filename);
                    List<String> lines = null;
                    try {
                        lines = FileUtils.readLines(file, DEFAULT_ENCODING);
                    } catch (IOException ex) {
                        fail("Error loading file: " + _filename);
                    }
                    
                    // remove "empty lines" (those with length 2 or less)
                    lines.removeIf(new Predicate<String>() {
                        @Override public boolean test(String t) { return t.length() <= 2; }
                    });
                    
                    updateMessage("Parsing File");
                    rowValues = new double[lines.size()][];

                    // parse values concurrently, if possible
                    int numThreads = Runtime.getRuntime().availableProcessors()-1;  // leave one thread for the application
                    if(numThreads < 2)
                        parseLines(lines);
                    else
                        parseLinesConcurrent(lines, numThreads);

                    // cache first column
                    firstColumn = new double[getTimeSeriesLength()];
                    for (int i = 0; i < firstColumn.length; i++) {
                        firstColumn[i] = rowValues[i][0] + 1950;
                    }
                    
                    return null;
                }

                /**
                 * Instantiates a number of threads to parallelize the parsing of the array.
                 * Speedup is (for four cores) not significant (usually 1.8s instead of 2.5s) but it also doesn't slow things down.
                 * @param lines
                 */
                private void parseLinesConcurrent(final List<String> lines, int numThreads) {

//                    long before = System.currentTimeMillis();
                    Thread[] processors = new Thread[numThreads];

                    // divide the string array in approximately equally sized partitions
                    int step = lines.size()/numThreads;

                    // create a thread for each partition and start it
                    for (int i = 0; i < numThreads; i++) {

                        final int from = i * step;
                        final int to = i == numThreads-1 ? lines.size()-1 : (i+1)*step-1;

                        processors[i] = new Thread(new Runnable() {
                            final int fromIndex = from;
                            final int toIndex = to;
                            @Override
                            public void run() {
                                // parse each line in this thread's partition
                                for (int j = fromIndex; j <= toIndex; j++){
                                    rowValues[j] = separator.splitToDouble(lines.get(j));
//                                    updateProgress(j-fromIndex,toIndex-fromIndex+1); // slow

                                }
                            }
                        });
                        processors[i].start();
                    }

                    // wait for all threads having finished
                    for (int i = 0; i < numThreads; i++) {
                        try {
                            processors[i].join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

//                    System.out.println("needed time: "+(System.currentTimeMillis()-before));
                }

                private void parseLines(List<String> lines) {
                    long before = System.currentTimeMillis();
                    for (int i = 0; i < lines.size(); i++) {
                        rowValues[i] = separator.splitToDouble(lines.get(i));
                        updateProgress(i+1, lines.size());
                        if (isCancelled()) { break; }
                    }
                    System.out.println("needed time: "+(System.currentTimeMillis()-before));
                }

                @Override protected void failed() {
                    super.failed();
                    updateMessage(this.getException().getMessage());
                    System.err.println(this.getException());
                }
                
            };
            
            
        }
    }
    
}
