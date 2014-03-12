package Data.Correlation;

import Global.RuntimeConfiguration;

import Data.ComplexSequence;
import Data.Correlation.CorrelationMatrix.Column;
import Data.TimeSeries;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
/**
 * 
 * @author Carl Witt
 */
public class CorrelationMatrix implements ResultContainer<Column> {

    public CorrelogramMetadata metadata;
    List<Column> columns;
    private static int instanceCount = 0;
    public final int id;
    
    /** Minimum and maximum correlation mean across all columns of the matrix. */
    Double minMean, maxMean;
    Double minStdDev, maxStdDev;
    
    public CorrelationMatrix(CorrelogramMetadata metadata) {
        this.id = ++instanceCount;
        this.columns = new ArrayList<Column>();
        this.metadata = metadata;
        CorrelogramStore.append(this);
    }
    
    /** Creates a correlation matrix from a list of other time series 
     *  by first computing the cross correlation for all of them
     *  and then computing the average and standard deviation. */
    public CorrelationMatrix(List<TimeSeries> timeSeries, int windowSize){
        
        this.id = ++instanceCount;
        this.metadata = new CorrelogramMetadata(timeSeries, windowSize);
        
        // check that all time series have the same length.
        
        int n = timeSeries.size();
        // the pairwise windowed cross correlation matrices
        CorrelationMatrix[] partialResults = new CorrelationMatrix[(n*(n-1))/2];
        int freeSlot = 0; // where to put the next partial result in the array
        
        long before = System.currentTimeMillis();
        for(int i = 0; i < timeSeries.size(); i++){
            for (int j = i+1; j < timeSeries.size(); j++) {
                partialResults[freeSlot++] =
                    CorrelogramStore.getResult(new CorrelogramMetadata(timeSeries.get(i), timeSeries.get(j), windowSize));
                if(!RuntimeConfiguration.VERBOSE){} else {
                    System.out.println(String.format("%d/%d: %s",freeSlot,partialResults.length,partialResults[freeSlot-1]));
                }
            }
        }
        
        if(true || RuntimeConfiguration.VERBOSE){
            System.out.println("Needed time for correlation: "+(System.currentTimeMillis()-before)+" ms");
        }
        
        
        before = System.currentTimeMillis();
        
        this.columns = new ArrayList<Column>();
        // create the result column by column
        for (int windowIdx = 0; windowIdx < partialResults[0].columns.size(); windowIdx++) {
            Column window = partialResults[0].columns.get(windowIdx);
            
            double[] means = new double[window.mean.length];
            double[] stdDevs = new double[window.mean.length];
            
            // vertical dimension of the correlogram
            for (int timeLag = 0; timeLag < means.length; timeLag++) {
                
                // mean computation: iterate over partial results 
                double mean = 0;
                for (int ccPairIdx = 0; ccPairIdx < partialResults.length; ccPairIdx++) {
                    mean += partialResults[ccPairIdx].columns.get(windowIdx).mean[timeLag];
                }
                mean /= partialResults.length;
                
                // std dev computation: iterate over partial results 
                double stdDev = 0;
                for (int ccPairIdx = 0; ccPairIdx < partialResults.length; ccPairIdx++) {
                    stdDev += Math.pow(partialResults[ccPairIdx].columns.get(windowIdx).mean[timeLag] - mean, 2);
                }
                stdDev = Math.sqrt(stdDev / partialResults.length);
                
                
                means[timeLag] = mean;
                stdDevs[timeLag] = stdDev;
            }
            
            ComplexSequence windowValues = ComplexSequence.create(means,stdDevs);
            this.columns.add(new Column(windowValues));
            
        }
        if(true || RuntimeConfiguration.VERBOSE){
            System.out.println("Needed time for aggregation: "+(System.currentTimeMillis()-before)+" ms");
        }
        
//        System.out.println("Aggregated correlation matrix (constructor):\n"+this);
    }
    
    public String toString(){
        return Joiner.on("\n").join(columns);
    }
    
    @Override
    public int getSize() {
        return columns.size();
    }

    @Override
    public boolean contains(int id) {
        return id >= 0 && id < columns.size();
    }

    @Override
    public boolean isEmpty() {
        return columns.isEmpty();
    }

    @Override
    public List<Column> getResultItems() {
        return columns;
    }

    @Override
    public void append(Column c) {
        columns.add(c);
    }
    
    public CorrelogramMetadata getMetadata(){
        return metadata;
    }
    @Override
    public Column getItembyID(int id) {
        return columns.get(id);
    }
    
    public double getMean(int window, int timeLag){
        return columns.get(window).mean[timeLag]; //  == Double.NaN ? 0 : columns.get(window).mean[timeLag];
    }
    public double getStdDev(int window, int timeLag){
        return columns.get(window).stdDev[timeLag]; //  == Double.NaN ? 0 : columns.get(window).mean[timeLag];
    }
    
    /** returns the smallest lag (given that the last half of the lags is displayed as negative lags). */
    public int getMinY(){
        // lags with tau != 0
        int realLags = getItemCount(0) - 1;
        int positiveLags = (int) Math.ceil(realLags/2.);
        int negativeLags = realLags - positiveLags;
        return -negativeLags;
    }
    /** Returns the largest lag (given that the last half of the lags is displayed as negative lags). */
    public int getMaxY(){
        // lags with tau != 0
        int realLags = getItemCount(0) - 1;
        int positiveLags = (int) Math.ceil(realLags/2.);
        return positiveLags;
    }
    
    /**
     * Interprets the last half of the possible time lags as negative lags.
     * A time series of length 6 can be aligned in six positions.
     * lag    0  1  2  3  4  5
     * split  0  1  2  3 -2 -1
     * So the largest time lag is equivalent to time lag -1, second last equals -2, etc.
     * @param timeLag a time lag in range [0..N-1] 
     * @return a time lag in range [-floor(N/2)..ceil(N/2)]
     */
    public int splitLag(int timeLag){
        int N = getItemCount(0);
        int realLags = N - 1;       // lags with tau != 0
        int positiveLags = (int) Math.ceil(realLags/2.);
        return timeLag <= positiveLags ? timeLag : -N + timeLag;
    }
    
    /** @return The window that contains the time value. */
    protected int containingWindow(int time){
        return time/metadata.windowSize;
    }
    
    // ------------------------------------------------------------
    // XYZ dataset implementation
    // ------------------------------------------------------------
    
    
    // the range of x values
    public int getSeriesCount() {
        return columns.size();
    }
    
    public int getItemCount(int window) {
        return columns.get(window).mean.length;
    }
    
    
    public Number getX(int time, int timeLag) {
        return new Double(getXValue(time, timeLag));
    }
    
    public double getXValue(int time, int timeLag) {
        return time;
    }
    
    
    public Number getY(int time, int timeLag) {
        return new Double(getYValue(time, timeLag));
    }
    
    public double getYValue(int time, int timeLag) {
        // returns a negative y (time lag) value for the last half of the correlation values
        return splitLag(timeLag);
    }
    
    public Number getZ(int time, int timeLag) {
        return new Double(getZValue(time, timeLag));
    }
    
    public double getZValue(int time, int timeLag) {
        return getMean(time, timeLag);
    }
    public Number getZ2(int time, int timeLag) {
        return new Double(getZ2Value(time, timeLag));
    }
    public double getZ2Value(int time, int timeLag) {
        return getStdDev(time, timeLag);
    }
    
    public Comparable getSeriesKey(int window) {
        return String.format("Correlogram matrix %d", id);
    }
    
    public int indexOf(Comparable windowKey) {
        return 0;
    }
    
    /** @return the minimum correlation value across all columns. */
    public double getMeanMinValue() {
        if(minMean != null)
            return minMean;
        minMean = Double.POSITIVE_INFINITY;
        for (Iterator<Column> it = columns.iterator(); it.hasNext();) {
            Column column = it.next();
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
    
    /** Sort of result item although not comparable (no getMinItem/getMaxItem sensible) */
    public static class Column  {

        /** Holds the data for this column as sequence of complex numbers. 
         * The real values are the means and the imaginary values are the standard deviations. 
         */
        private final ComplexSequence values;
        
        /** Aliases for clearer code. Points to the data stored in the values field.
         * Using the aliases, references to re and im cna be avoided. */
        public double[] mean;
        public double[] stdDev;
        
        public Column(double[] mean, double[] stdDev) {
            this.values = ComplexSequence.create(mean, stdDev);
            this.mean = values.re;
            this.stdDev = values.im;
        }
        public Column(ComplexSequence cs){
            this.values = cs;
            this.mean = cs.re;
            this.stdDev = cs.im;
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
            if (!Arrays.equals(this.mean, other.mean)) {
                return false;
            }
            return true;
        }

    }

    
    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + this.id;
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
//        if (this.id != other.id) {
//            return false;
//        }
        if (this.metadata != other.metadata && (this.metadata == null || !this.metadata.equals(other.metadata))) {
            return false;
        }
        if (!this.columns.equals(other.columns)) {
            return false;
        }
        return true;
    }
}
