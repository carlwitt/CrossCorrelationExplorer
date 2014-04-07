package Data.Correlation;

import Global.RuntimeConfiguration;

import Data.ComplexSequence;
import Data.Correlation.CorrelationMatrix.Column;
import Data.TimeSeries;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
/**
 * 
 * TODO: clean up methods that have been introduced to fit the JFreeChart interfaces
 * @author Carl Witt
 */
public class CorrelationMatrix {

    /** Stores the input of the computation. */
    public CorrelogramMetadata metadata;
    /** The column wise output of the computation. */
    List<Column> columns;
    
    /** Minimum and maximum correlation mean across all columns of the matrix. */
    Double minMean, maxMean;
    Double minStdDev, maxStdDev;
    
    public final ComputeService computeService = new ComputeService();
    
    /**
     * Sets the metadata for a correlation matrix, WITHOUT computing the actual contents (because the data structure is used in different ways).
     * @param metadata The input time series, window size, NaN value handling strategy, etc.
     */
    public CorrelationMatrix(CorrelogramMetadata metadata) {
        this.columns = new ArrayList<>();
        this.metadata = metadata;
    }
    
    /** Creates a correlation matrix from a list of other time series by first computing the cross correlation for all of them and then computing the average and standard deviation. */
    public void compute(){
        
        DFT.naAction = metadata.naAction;
        
        // check that all time series have the same length?
        
        // get/compute all the elementary (one-by-one) cross correlations
        CorrelationMatrix[] partialResults = new CorrelationMatrix[metadata.setA.size()*metadata.setB.size()];
        int freeSlot = 0; // where to put the next partial result in the array
        long before = System.currentTimeMillis();
        for(TimeSeries tsA : metadata.setA){
            for (TimeSeries tsB : metadata.setB) {
                partialResults[freeSlot++] = CorrelogramStore.getResult(new CorrelogramMetadata(tsA, tsB, metadata.windowSize, metadata.naAction));
//if(RuntimeConfiguration.VERBOSE) System.out.println(String.format("%d/%d ",freeSlot,partialResults.length));//,partialResults[freeSlot-1]
            }
        }
        
        if(RuntimeConfiguration.VERBOSE){
            System.out.println("Needed time for correlation: "+(System.currentTimeMillis()-before)+" ms");
        }
        
        before = System.currentTimeMillis();
        
        this.columns = new ArrayList<>();
        // create the result column by column
        for (int windowIdx = 0; windowIdx < partialResults[0].columns.size(); windowIdx++) {
            Column window = partialResults[0].columns.get(windowIdx);
            
            double[] means = new double[window.mean.length];
            double[] stdDevs = new double[window.mean.length];
            
            // vertical dimension of the correlogram
            for (int timeLag = 0; timeLag < means.length; timeLag++) {
                
                // mean computation: iterate over partial results 
                double mean = 0;
                for (CorrelationMatrix partialResult : partialResults) {
                    mean += partialResult.columns.get(windowIdx).mean[timeLag];
                }
                mean /= partialResults.length;
                
                // std dev computation: iterate over partial results 
                double stdDev = 0;
                for (CorrelationMatrix partialResult : partialResults) {
                    // ∑ (x - µ)^2
                    stdDev += Math.pow(partialResult.columns.get(windowIdx).mean[timeLag] - mean, 2);
                }
                stdDev = Math.sqrt(stdDev / partialResults.length);
                
                means[timeLag] = mean;
                stdDevs[timeLag] = stdDev;
            }
            
            ComplexSequence windowValues = ComplexSequence.create(means,stdDevs);
            this.columns.add(new Column(windowValues, window.windowXOffset));
            
        }
        if(RuntimeConfiguration.VERBOSE){
            System.out.println("Needed time for aggregation: "+(System.currentTimeMillis()-before)+" ms");
        }
        
        CorrelogramStore.append(this);
        
//        System.out.println("Aggregated correlation matrix (constructor):\n"+this);
    }
    
    /** Reusable concurrent execution logic for loading and parsing files with progress reporting.
     * The service can be used to load and parse the file contents asynchronously (non-UI-blocking) and to display progress.  */
    public class ComputeService extends Service<CorrelationMatrix> {
                
//        CorrelationMatrix input, result;
//        public ComputeService(CorrelationMatrix input){
//            this.input = input;
//        }
        
        @Override protected Task<CorrelationMatrix> createTask() {
            return new Task<CorrelationMatrix>() {
                
                @Override protected CorrelationMatrix call() {
                    
                    DFT.naAction = metadata.naAction;
        
                    // check that all time series have the same length?

                    
                    // get/compute all the elementary (one-by-one) cross correlations
                    CorrelationMatrix[] partialResults = new CorrelationMatrix[metadata.setA.size()*metadata.setB.size()];
                    int freeSlot = 0; // where to put the next partial result in the array
                    long before = System.currentTimeMillis();
                    int processedCCs = 0;
                    for(TimeSeries tsA : metadata.setA){
                        for (TimeSeries tsB : metadata.setB) {
                            processedCCs++;
                            updateMessage(String.format("Retrieving windowed cross correlation between time series %s and %s.", tsA.getId(), tsB.getId()));
                            updateProgress(processedCCs, partialResults.length);
                            
                            partialResults[freeSlot++] = CorrelogramStore.getResult(new CorrelogramMetadata(tsA, tsB, metadata.windowSize, metadata.naAction));
//if(RuntimeConfiguration.VERBOSE) System.out.println(String.format("%d/%d ",freeSlot,partialResults.length));//,partialResults[freeSlot-1]
                        }
                    }

                    if(RuntimeConfiguration.VERBOSE){
                        System.out.println("Needed time for correlation: "+(System.currentTimeMillis()-before)+" ms");
                    }

                    before = System.currentTimeMillis();

                    //List<Column> 
                    List<Column> _columns = new ArrayList<>();
                    // create the result column by column
                    for (int windowIdx = 0; windowIdx < partialResults[0].columns.size(); windowIdx++) {
                        
                        updateMessage(String.format("Aggregating correlation results in window %s",windowIdx+1));
                        updateProgress(windowIdx, partialResults[0].columns.size());
                        
                        Column window = partialResults[0].columns.get(windowIdx);

                        double[] means = new double[window.mean.length];
                        double[] stdDevs = new double[window.mean.length];

                        // vertical dimension of the correlogram
                        for (int timeLag = 0; timeLag < means.length; timeLag++) {

                            // mean computation: iterate over partial results 
                            double mean = 0;
                            for (CorrelationMatrix partialResult : partialResults) {
                                mean += partialResult.columns.get(windowIdx).mean[timeLag];
                            }
                            mean /= partialResults.length;

                            // std dev computation: iterate over partial results 
                            double stdDev = 0;
                            for (CorrelationMatrix partialResult : partialResults) {
                                // ∑ (x - µ)^2
                                stdDev += Math.pow(partialResult.columns.get(windowIdx).mean[timeLag] - mean, 2);
                            }
                            // sqrt(1/N * ANS)
                            stdDev = Math.sqrt(stdDev / partialResults.length);

                            means[timeLag] = mean;
                            stdDevs[timeLag] = stdDev;
                        }

                        ComplexSequence windowValues = ComplexSequence.create(means,stdDevs);
                        _columns.add(new Column(windowValues, window.windowXOffset));

                    }
                    if(RuntimeConfiguration.VERBOSE){
                        System.out.println("Needed time for aggregation: "+(System.currentTimeMillis()-before)+" ms");
                    }

                    CorrelationMatrix.this.columns = _columns;
                    CorrelogramStore.append(CorrelationMatrix.this);

                    return CorrelationMatrix.this;
                }
                
                @Override protected void cancelled() {
                    super.cancelled();
                    System.out.println("correlation computation aborted.");
                }
                
            };
            
        }
    }
    
    public double getMinX(){
        return columns.get(0).windowXOffset;
    }
    public double getMaxX(){
        Column lastColumn = columns.get(columns.size()-1);
        return lastColumn.windowXOffset + lastColumn.mean.length;
    }
    
    @Override public String toString(){
        return "Correlation Matrix\n"+Joiner.on("\n").join(columns);
    }
    
    public int getSize() { return columns.size(); }

    public List<Column> getResultItems() { return columns; }

    public void append(Column c) { columns.add(c); }
    
    public CorrelogramMetadata getMetadata(){ return metadata; }
    
    public Column getItembyID(int id) { return columns.get(id); }
    
    public double getMean(int window, int timeLag){
        return columns.get(window).mean[timeLag]; //  == Double.NaN ? 0 : columns.get(window).mean[timeLag];
    }
    public double getStdDev(int window, int timeLag){
        return columns.get(window).stdDev[timeLag]; //  == Double.NaN ? 0 : columns.get(window).mean[timeLag];
    }
    
    
    
    /**
     * Interprets the last half of the possible time lags as negative lags.
     * A time series of length 6 can be shifted by six different time lags.
     * lag    0  1  2  3  4  5
     * split  0  1  2  3 -2 -1
     * So the largest time lag is equivalent to time lag -1, second last equals -2, etc.
     * @param timeLag a time lag in range [0..N-1] 
     * @param numElements the length of the column
     * @return a time lag in range [-floor(N/2)..ceil((N-1)/2)]
     */
    public static int splitLag(int timeLag, int numElements){
        int splitPoint = (int) Math.ceil(0.5 * (numElements-1));
        return timeLag > splitPoint ? timeLag - numElements : timeLag;
    }
    public static int minLag(int numElements){
        return (int) -Math.floor(0.5 * (numElements-1));
    }
    public static int maxLag(int numElements){
        return (int) Math.floor(0.5 * numElements);
    }
    
    /** @return the smallest lag (given that the last half of the lags is displayed as negative lags). */
        public int getMinY(){
            // lags with tau != 0
    //        int realLags = getItemCount(0) - 1;
    //        int positiveLags = (int) Math.ceil(realLags/2.);
    //        int negativeLags = realLags - positiveLags;
    //        return -negativeLags;
            int N = columns.get(0).mean.length;
            return minLag(N);
//            return 0;
        }
        /** @return the largest lag (given that the last half of the lags is displayed as negative lags). */
        public int getMaxY(){
            int N = columns.get(0).mean.length;
            return maxLag(N);
//            return columns.get(0).mean.length;
        }    
    
    public int getItemCount(int window) {
        return columns.get(window).mean.length;
    }
    
    
    /** @return the minimum correlation value across all columns. */
    public double getMeanMinValue() {
        if(minMean != null)
            return minMean;
        minMean = Double.POSITIVE_INFINITY;
        for (Column column : columns) {
            minMean = Math.min(minMean, column.getMeanMin());
        }
        return minMean;
    }
    /** @return the maximum correlation mean across all columns. */
    public double getMeanMaxValue() {
        if(maxMean != null)
            return maxMean;
        maxMean = Double.NEGATIVE_INFINITY;
        for (Column column : columns) {
            maxMean = Math.max(maxMean, column.getMeanMax());
        }
        return maxMean;
    }
    
    /** @return the minimum correlation value across all columns. */
    public double getStdDevMinValue() {
        if(minStdDev != null)
            return minStdDev;
        minStdDev = Double.POSITIVE_INFINITY;
        for (Column column : columns) {
            minStdDev = Math.min(minStdDev, column.getStdDevMin());
        }
        return minStdDev;
    }
    /** @return the maximum correlation mean across all columns. */
    public double getStdDevMaxValue() {
        if(maxStdDev != null)
            return maxStdDev;
        maxStdDev = Double.NEGATIVE_INFINITY;
        for (Column column : columns) {
            maxStdDev = Math.max(maxStdDev, column.getStdDevMax());
        }
        return maxStdDev;
    }
    
    /** Represents one aggregated cross-correlation result within a time window */
    public static class Column  {

        /** Holds the data for this column as sequence of complex numbers. 
         * The real values are the means and the imaginary values are the standard deviations. 
         */
        private final ComplexSequence values;
        
        /** Aliases for clearer code. Points to the data stored in the values field.
         * Using the aliases, references to re and im cna be avoided. */
        public double[] mean;
        public double[] stdDev;
        
        /** Each columns represents the results of a window of the x-axis. This is the x-value where the window starts (where it ends follows from the column length). */
        public final double windowXOffset;
        
        /**
         * Takes two arrays to represent 2D values (or pairs) as entries of the column
         * @param mean The first dimension of the result values (correlation mean)
         * @param stdDev The second dimension of the result values (correlation standard deviation)
         * @param windowXOffset see {@link Column#windowXOffset} 
         */
        public Column(double[] mean, double[] stdDev, double windowXOffset) {
            this.values = ComplexSequence.create(mean, stdDev);
            this.mean = values.re;
            this.stdDev = values.im;
            this.windowXOffset = windowXOffset;
        }
        public Column(ComplexSequence cs, double windowXOffset){
            this.values = cs;
            this.mean = cs.re;
            this.stdDev = cs.im;
            this.windowXOffset = windowXOffset;
        }
        
        public double getMeanMin(){ return values.getMin(ComplexSequence.Part.REAL);}
        public double getMeanMax(){ return values.getMax(ComplexSequence.Part.REAL);}
        public double getStdDevMin(){ return values.getMin(ComplexSequence.Part.IMAGINARY);}
        public double getStdDevMax(){ return values.getMax(ComplexSequence.Part.IMAGINARY);}
        
        @Override
        public String toString(){
            return String.format("mean: %s\nstd dev: %s",Arrays.toString(values.re),Arrays.toString(values.im));
        }
        
        @Override
        public int hashCode() {
            int hash = 5;
            hash = 29 * hash + Arrays.hashCode(this.mean);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Column other = (Column) obj;
            if(this.windowXOffset != other.windowXOffset){
                return false;
            }
            if (!Arrays.equals(this.mean, other.mean)) {
                return false;
            }
            return true;
        }

    }

    
    @Override
    public int hashCode() {
        int hash = 3;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CorrelationMatrix other = (CorrelationMatrix) obj;
        
        if (this.metadata != other.metadata && (this.metadata == null || !this.metadata.equals(other.metadata))) {
            return false;
        }
        if (!this.columns.equals(other.columns)) {
            return false;
        }
        return true;
    }
    
}
