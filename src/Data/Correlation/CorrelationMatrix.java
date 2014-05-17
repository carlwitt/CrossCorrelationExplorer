package Data.Correlation;

import Global.RuntimeConfiguration;

import Data.ComplexSequence;
import Data.TimeSeries;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

/**
 * The Correlation Matrix aggregates and stores the results of a windowed cross correlation between two sets of time series.
 * Each matrix is comprised of a list of {@link Data.Correlation.CorrelationMatrix.Column}s.
 *
 * @author Carl Witt
 */
public class CorrelationMatrix {

    /** Stores the input of the computation. */
    public final CorrelationMetadata metadata;
    
    /** The column wise output of the computation. */
    List<Column> columns;
    
    /** Minimum and maximum correlation mean across all columns of the matrix. */
    private Double minMean;
    private Double maxMean;
    private Double minStdDev;
    private Double maxStdDev;
    
    public final ComputeService computeService = new ComputeService();
    
    /**
     * Sets the metadata for a correlation matrix, WITHOUT computing the actual contents (because the data structure is used in different ways).
     * @param metadata The input time series, window size, NaN value handling strategy, etc.
     */
    public CorrelationMatrix(CorrelationMetadata metadata) {
        this.columns = new ArrayList<>();
        this.metadata = metadata;
    }
    
    /** Creates a correlation matrix from a list of other time series by first computing the cross correlation for all of them and then computing the average and standard deviation. */
    public void compute(){
        
//        DFT.naAction = metadata.naAction;
        
        // check that all time series have the same length?
        
        // get/compute all the elementary (one-by-one) cross correlations
        CorrelationMatrix[] partialResults = new CorrelationMatrix[metadata.setA.size()*metadata.setB.size()];
        int freeSlot = 0; // where to put the next partial result in the array
        long before = System.currentTimeMillis();
        for(TimeSeries tsA : metadata.setA){
            for (TimeSeries tsB : metadata.setB) {
                partialResults[freeSlot++] = CorrelogramStore.getResult(new CorrelationMetadata(tsA, tsB, metadata.windowSize, metadata.tauMin, metadata.tauMax, metadata.naAction, metadata.baseWindowOffset));
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
            this.columns.add(new Column(windowValues, window.windowStartIndex, partialResults[0].columns.get(windowIdx).tauMin));
            
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
                    
//                    DFT.naAction = metadata.naAction;
        
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
                            
                            partialResults[freeSlot++] = CorrelogramStore.getResult(new CorrelationMetadata(tsA, tsB, metadata.windowSize, metadata.tauMin, metadata.tauMax, metadata.naAction, metadata.baseWindowOffset));
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
                        _columns.add(new Column(windowValues, window.windowStartIndex, partialResults[0].getItembyID(windowIdx).tauMin));

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

    /** @return the index of the first time series value where the first column (time window) starts. */
    int getStartOffsetInTimeSeries(){
        if(columns.size() == 0)return 0; // the matrix can have no columns if the winodw size exceeds the length of the time series
        return columns.get(0).windowStartIndex;
    }
    /** @return the index of the last time series value of the last column (time window). */
    int getEndOffsetInTimeSeries(){
        if(columns.size() == 0)return 0; // the matrix can have no columns if the winodw size exceeds the length of the time series
        Column lastColumn = columns.get(columns.size()-1);
        return lastColumn.windowStartIndex + lastColumn.tauMin + lastColumn.mean.length - 1;
    }

    /** @return the first point in time covered by the matrix. */
    public double getStartXValueInTimeSeries(){
        return metadata.setA.get(0).getDataItems().re[getStartOffsetInTimeSeries()];
    }
    /** @return the last point in time covered by the matrix. */
    public double getEndXValueInTimeSeries(){
        return metadata.setA.get(0).getDataItems().re[getEndOffsetInTimeSeries()];
    }
    
    @Override public String toString(){
        return "Correlation Matrix\n"+Joiner.on("\n").join(columns);
    }

    /** @return the number of columns (=windows) in the matrix. */
    public int getSize() { return columns.size(); }

    /** @return the columns (=windows) in the matrix. */
    public List<Column> getResultItems() { return columns; }

    public void append(Column c) { columns.add(c); }
    
    public CorrelationMetadata getMetadata(){ return metadata; }
    
    public Column getItembyID(int id) { return columns.get(id); }

    /** @return the smallest lag used on any column. */
    public int minLag(){
        if(columns.size()==0)return 0;
        return columns.get(columns.size()-1).tauMin;
    }
    /** @return the largest lag used on any column */
    public int maxLag(){
        if(columns.size()==0)return 0;
        return columns.get(0).tauMin + columns.get(0).mean.length - 1;
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

        /**
         * Holds the data for this column as sequence of complex numbers.
         * The real values are the means and the imaginary values are the standard deviations. 
         */
        private final ComplexSequence values;
        
        /** Aliases for clearer code. Points to the data stored in the values field.
         * Using the aliases, references to re and im can be avoided. */
        public final double[] mean;
        public final double[] stdDev;
        
        /** Each columns represents the results of a window of the x-axis. This is the x-value where the window starts (where it ends follows from the column length). */
        public final int windowStartIndex;

        public final int tauMin;
        
        /**
         * Takes two arrays to represent 2D values (or pairs) as entries of the column
         * @param mean The first dimension of the result values (correlation mean)
         * @param stdDev The second dimension of the result values (correlation standard deviation)
         * @param windowStartIndex see {@link Data.Correlation.CorrelationMatrix.Column#windowStartIndex}
         * @param tauMin
         */
        public Column(double[] mean, double[] stdDev, int windowStartIndex, int tauMin) {
            this.tauMin = tauMin;
            this.values = ComplexSequence.create(mean, stdDev);
            this.mean = values.re;
            this.stdDev = values.im;
            this.windowStartIndex = windowStartIndex;
        }
        public Column(ComplexSequence cs, int windowStartIndex, int tauMin){
            this.values = cs;
            this.tauMin = tauMin;
            this.mean = cs.re;
            this.stdDev = cs.im;
            this.windowStartIndex = windowStartIndex;
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
            if(this.windowStartIndex != other.windowStartIndex){
                return false;
            }
            if(this.tauMin != other.tauMin) return false;

            for (int i = 0; i < this.mean.length; i++)
                if(Math.abs(this.mean[i]-other.mean[i])>1e-5) return false;
            for (int i = 0; i < this.stdDev.length; i++)
                if(Math.abs(this.stdDev[i]-other.stdDev[i])>1e-5) return false;
//            if (!Arrays.equals(this.mean, other.mean)) return false;
//            if (!Arrays.equals(this.stdDev, other.stdDev)) return false;
            return true;
        }

        public int getSize() {
            return values.size();
        }
    }

    
    @Override
    public int hashCode() {
        return 3;
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
