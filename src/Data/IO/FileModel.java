package Data.IO;

import Data.DataModel;
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
import java.util.function.Predicate;

/**
 * Abstracts from the way a number of time seriese is stored in a text file.
 * This specific model parses a number matrix from the string data.
 * It also encapsulates the conventions on how to interpret these numbers (i.e. how x- and y-values are stored within the matrix).
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

    public void persist(DataModel data, String targetPath) throws IOException {

//        ArrayList<Double> lineValues = new ArrayList<>(data.size()+1); // all time series and their common x values
        ArrayList<String> lines = new ArrayList<>(data.getTimeSeriesLength()+1);

        TimeSeries firstTimeSeries = data.firstEntry().getValue();
        for (int i = 0; i < data.getTimeSeriesLength(); i++) {
//            lineValues.clear();
//            lineValues.add(data.firstEntry().getValue().getDataItems().re[i]);
            String line = "";

            line += String.format("%-16s", firstTimeSeries.getDataItems().re[i]);

            for(TimeSeries ts : data.values()){
                //lineValues.add(ts.getDataItems().get(i));
                line += String.format("%-16s", ts.getDataItems().get(i));
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
                    
                    // parse values
                    updateMessage("Parsing File");
                    rowValues = new double[lines.size()][];
                    for (int i = 0; i < lines.size(); i++) {
                        rowValues[i] = separator.splitToDouble(lines.get(i));
                        updateProgress(i+1, lines.size());
                        if (isCancelled()) { break; }
                    }
                    
                    // cache first column
                    firstColumn = new double[getTimeSeriesLength()];
                    for (int i = 0; i < firstColumn.length; i++) {
                        firstColumn[i] = rowValues[i][0] + 1950;
                    }
                    
                    return null;
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
