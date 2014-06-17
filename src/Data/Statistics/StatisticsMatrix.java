package Data.Statistics;

import Data.Correlation.CorrelationMatrix;
import Data.Windowing.WindowMetadata;
import com.google.common.base.Joiner;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class stores statistics (e.g. mean, standard deviation, median) for combinations of time windows.
 * It describes the distributions that results from applying some operator (e.g. pearson product-moment correlation) to combinations time windows of two sets of time series.
 *
 * Data in the first window (the base window, corresponds to a column of the matrix) is taken from each time series in input set A,
 * data in the second window (the lag window, corresponds to row in the matrix) is taken from each time series in input set B (see {@link Data.SharedData#correlationSetA} and {@link Data.SharedData#correlationSetB}).
 * Each combination of two time windows gives one correlation value, and contributes to the correlation distribution in the according cell of the matrix.
 * The statistics are then created for each cell over these correlation value distributions.
 *
 * Each matrix is comprised of a list of {@link Column}s which represent all the results belonging to a single window in the first time series.
 *
 * @author Carl Witt
 */
public abstract class StatisticsMatrix {

    /** These constants can be used to conveniently refer to certain statistics.
     * {@link #MEAN}: average. {@link #STD_DEV}: standard deviation. {@link #MEDIAN}: the 50th percentile.
     */
    private final static int MEAN = 0;
    private final static int STD_DEV = 1;
    private final static int MEDIAN = 2;
    private static final int NUM_STATS = 3;                     // how many statistics are measured
    private final static int MINIMUM = 0;
    private final static int MAXIMUM = 1;
    private static final int NUM_META_STATS = 2;                // how many statistics about the statistics are measured (minimum and maximum)

    /** Stores the input of the computation. */
    private final WindowMetadata metadata;

    /** Contains the minimum/maximum value of the given statistic, where the first dimension
     *  refers to the statistic (index using {@link #MEAN}, {@link #STD_DEV}, ...) and the second
     *  dimension specifies the kind of the extremum (index using {@link #MINIMUM}, {@link #MAXIMUM}).
     */
    private final Double[][] extrema = new Double[NUM_STATS][NUM_META_STATS];

    /** The column wise output of the computation. */
    private final List<Column> columns;

    public StatisticsMatrix(WindowMetadata metadata) {
        this.columns = new ArrayList<>();
        this.metadata = metadata;
    }

    public abstract void compute();

    /** @return the index of the first time series value where the first column (time window) starts. */
    int getStartOffsetInTimeSeries(){
        if(columns.size() == 0) return 0; // the matrix can have no columns if the winodw size exceeds the length of the time series
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

    /** @return the columns (=windows) in the matrix. */
    public List<? extends Column> getResultItems() { return columns; }

    public void append(Column c) { columns.add(c); }

    public WindowMetadata getMetadata(){ return metadata; }

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

    /**
     * @param STATISTIC one of the constants {@link #MEAN}, {@link #STD_DEV}, {@link #MEDIAN}, ...
     * @return the minimum value of the specified statistic across all cells in the matrix.
     */
    public double getMin(int STATISTIC) {
        return getExtremum(STATISTIC, MINIMUM);
    }

    /**
     * @param STATISTIC one of the constants {@link #MEAN}, {@link #STD_DEV}, {@link #MEDIAN}, ...
     * @return the maximum value of the specified statistic across all cells in the matrix.
     */
    public double getMax(int STATISTIC) {
        return getExtremum(STATISTIC, MAXIMUM);
    }

    double getExtremum(int STATISTIC, int MINMAX){
        if(extrema[STATISTIC][MINMAX] == null){
            DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics();
            for(Column c : columns) descriptiveStatistics.addValue(c.getExtremum(STATISTIC, MINMAX));
            extrema[STATISTIC][MINIMUM] = descriptiveStatistics.getMin();
            extrema[STATISTIC][MAXIMUM] = descriptiveStatistics.getMax();
        }
        return extrema[STATISTIC][MINMAX];
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Column Data Structure
    // -----------------------------------------------------------------------------------------------------------------

    /** Represents one aggregated cross-correlation result within a time window */
    public class Column  {

        double[][] data = new double[NUM_STATS][];

        /** Contains the minimal/maximal value of the given statistic along this column.
         * E.g. extrema[STD_DEV][MINIMUM] gives the minimum standard deviation among all time lags for the window represented by this column.
         */
        final Double[][] extrema = new Double[NUM_STATS][NUM_META_STATS];

        /** Aliases for clearer code. Points to the data stored in the values field.
         * Using the aliases, references to re and im can be avoided. */
        public double[] mean, stdDev, median;

        /** Each columns represents the results of a window of the x-axis. This is the x-value where the window starts (where it ends follows from the column length). */
        public final int windowStartIndex;

        /** The first value in the column corresponds to this time lag. (Since only complete windows are considered, this deviates for the first columns in the matrix.) */
        public final int tauMin;

        Column(ColumnBuilder builder){
            this.windowStartIndex = builder.windowStartIndex;
            this.tauMin = builder.tauMin;
            data = builder.data;
        }

//        public double getAnchorXValue(){
//            return metadata.setA.get(0).getDataItems().re[windowStartIndex];
//        }

        double getExtremum(int STATISTIC, int MINMAX){
            if(extrema[STATISTIC][MINMAX] == null){
                DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics(data[STATISTIC]);
                extrema[STATISTIC][MINIMUM] = descriptiveStatistics.getMin();
                extrema[STATISTIC][MAXIMUM] = descriptiveStatistics.getMax();
            }
            return extrema[STATISTIC][MINMAX];
        }

        /**
         * @param STATISTIC one of the constants {@link #MEAN}, {@link #STD_DEV}, {@link #MEDIAN}, ...
         * @return the minimum value of the specified statistic across all cells in this column.
         */
        public double getMin(int STATISTIC){ return getExtremum(STATISTIC, MINIMUM); }
        /**
         * @param STATISTIC one of the constants {@link #MEAN}, {@link #STD_DEV}, {@link #MEDIAN}, ...
         * @return the minimum value of the specified statistic across all cells in this column.
         */
        public double getMax(int STATISTIC){ return getExtremum(STATISTIC, MAXIMUM); }

        @Override
        public String toString(){
            return String.format("   mean: %s\nstd dev: %s\n median: %s", Arrays.toString(data[MEAN]),Arrays.toString(data[STD_DEV]),Arrays.toString(data[MEDIAN]));
        }

        public int getSize() {
            return data[MEAN].length;
        }

    }

    public class ColumnBuilder {

        public final double[][] data = new double[NUM_STATS][];

        public final int windowStartIndex;
        public final int tauMin;
        public ColumnBuilder(int windowStartIndex, int tauMin){
            this.windowStartIndex = windowStartIndex;
            this.tauMin = tauMin;
        }

        public ColumnBuilder mean(double[] mean){
            this.data[MEAN] = mean;
            return this;
        }
        public ColumnBuilder standardDeviation(double[] standardDeviation){
            this.data[STD_DEV] = standardDeviation;
            return this;
        }
        public ColumnBuilder median(double[] median){
            this.data[MEDIAN] = median;
            return this;
        }

        public Column build(){
            return new Column(this);
        }

    }

    // -----------------------------------------------------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------------------------------------------------

    @Override public String toString(){
        return "Correlation Matrix\n"+ Joiner.on("\n").join(columns);
    }

    /** @return the number of columns (=windows) in the matrix. */
    public int getSize() { return columns.size(); }

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

        return true;
    }
}
