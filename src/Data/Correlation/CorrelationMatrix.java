package Data.Correlation;

import Data.CorrelogramStore;
import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import com.google.common.base.Joiner;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class computes a statistics matrix by applying the pearson product-moment correlation to combinations of time series windows.
 */
public class CorrelationMatrix {

    protected final static int MINIMUM = 0, MAXIMUM = 1;
    protected static int NUM_META_STATS = 2;                // how many statistics about the statistics are measured (minimum and maximum)

    /** These constants can be used to conveniently refer to certain statistics.
     * {@link #POSITIVE_SIGNIFICANT} percentage of statistically significant (t-test) positive correlation values.
     * {@link #NEGATIVE_SIGNIFICANT} percentage of statistically significant negative correlation values.
     */
    public final static int MEAN = 0, STD_DEV = 1, MEDIAN = 2, IQR = 3, POSITIVE_SIGNIFICANT = 4, NEGATIVE_SIGNIFICANT = 5, ABSOLUTE_SIGNIFICANT = 6;
    protected static int NUM_STATS = 7;                     // how many statistics are measured

    public final ComputeService computeService = new ComputeService();

    protected List<CorrelationColumn> columns = new ArrayList<CorrelationColumn>();

    /** Stores the input of the computation. */
    public final WindowMetadata metadata;

    /** Contains the minimum/maximum value of the given statistic, where the first dimension
     *  refers to the statistic (index using {@link #MEAN}, {@link #STD_DEV}, ...) and the second
     *  dimension specifies the kind of the extremum (index using {@link #MINIMUM}, {@link #MAXIMUM}).
     */
    protected final Double[][] extrema = new Double[NUM_STATS][NUM_META_STATS];

    CorrelationSignificance significanceTester;

    /**
     * Sets the metadata for a correlation matrix, WITHOUT computing the actual contents.
     * @param metadata The input time series, window size, NaN value handling strategy, etc.
     */
    public CorrelationMatrix(WindowMetadata metadata) {

        this.metadata = metadata;

        if(metadata != null && metadata.windowSize > 2){ // for significance testing, we need at least one degree of freedom (degrees of freedom = window size - 2)
            significanceTester = new CorrelationSignificance(metadata.windowSize, getSignificanceLevel(metadata));
            System.out.println("Init matrix with p = "+metadata.customParameters.get("significanceLevel"));
        }

    }

    public static double getSignificanceLevel(WindowMetadata metadata){
        Double significanceLevel = (Double) metadata.customParameters.get("significanceLevel");
        assert(significanceLevel != null);
        return significanceLevel;
    }
    public static void setSignificanceLevel(WindowMetadata metadata, double significanceLevel){
        metadata.customParameters.put("significanceLevel", significanceLevel);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Computation
    // -----------------------------------------------------------------------------------------------------------------

    /** Creates a correlation matrix from a list of other time series by first computing the cross correlation for all of them and then computing the average and standard deviation. */
    public void compute(){

        this.columns = new ArrayList<>();

        // all time series in set A and set B are expected to be of equal length
        int timeSeriesLength = metadata.setA.get(0).getSize();

        // caches reusable terms (for base window computations) of the lag windows across all time series of set B
        LagWindowCache lagWindowCache = new LagWindowCache(metadata, metadata.tauMax-metadata.tauMin+1);
//System.out.println("Lag cache init size "+(metadata.tauMax-metadata.tauMin+1));

        // create the result column by column to avoid having to keep too much data in main memory
        for (int baseWindowStartIdx = 0; baseWindowStartIdx <= timeSeriesLength - metadata.windowSize; baseWindowStartIdx += metadata.baseWindowOffset) {

//System.out.println(String.format("Processing base window %s of %s",baseWindowStartIdx,timeSeriesLength - metadata.windowSize));

            // the first and last columns permit for only some of the time lags
            int minLagWindowStartIdx = Math.max(0, baseWindowStartIdx + metadata.tauMin);
            int maxLagWindowStartIdx = Math.min(timeSeriesLength - metadata.windowSize, baseWindowStartIdx + metadata.tauMax);
            int minTau = minLagWindowStartIdx - baseWindowStartIdx;
            int maxTau = maxLagWindowStartIdx - baseWindowStartIdx;

            // compute raw data
            double[][] cells = columnCorrelations(baseWindowStartIdx, minTau, maxTau, lagWindowCache);

            for(double[] cell : cells)
                System.out.println(Arrays.toString(cell));

            // aggregate raw data
            columns.add(aggregate(cells, baseWindowStartIdx, minTau));

//            if(RuntimeConfiguration.VERBOSE){
//                System.out.println("Needed time for aggregation: "+(System.currentTimeMillis()-before)+" ms");
//            }

        }

        CorrelogramStore.append(this);
        
    }

    /**
     * Computes all raw correlation values vor a single column of the correlation matrix.
     * @param baseWindowStartIdx the index of the value where the base window starts
     * @return all the correlation values in all the cells in this column (the raw data for aggregation). the first dimension refers to time lag, second to the computed correlation values.
     */
    protected double[][] columnCorrelations(int baseWindowStartIdx, int tauMin, int tauMax, LagWindowCache lagWindowCache) {

        // for rolling mean optimization
        double[] baseWindowMeans = new double[metadata.setA.size()];
        Arrays.fill(baseWindowMeans, Double.NaN);

        double[][] cells = new double[tauMax - tauMin + 1][metadata.setA.size() * metadata.setB.size()];

        // contains the normalized values of the base window of the current time series of set A (each of those base windows is touched only once)
        double[] normalizedValuesA = new double[metadata.windowSize];
        double[] normalizedValuesB; // the same for the current lag window in set B

        // contains the root of the sum of the squared normalized values of the base window of the current time series of set A (each of those base windows is touched only once)
        double rootOfSummedSquaresA;
        double rootOfSummedSquaresB; // the same for the current lag window in set B

//        int tsAFrom = 0; int tsATo = metadata.setA.size()-1;

        // iterate over all time series in set A
        for (int tsAIdx = 0; tsAIdx < metadata.setA.size(); tsAIdx++) {

//System.out.println("Processing tsA "+i);
            TimeSeries tsA = metadata.setA.get(tsAIdx);

            // compute the mean of the base window in an incremental manner, if possible
            baseWindowMeans[tsAIdx] = CrossCorrelation.incrementalMean(tsA,
                    baseWindowStartIdx, baseWindowStartIdx+metadata.windowSize-1,       // from, to
                    baseWindowMeans[tsAIdx], baseWindowStartIdx-metadata.baseWindowOffset);  // previous mean, previous from

            // compute the normalized values of the base window
            CrossCorrelation.normalizeValues(tsA.getDataItems().im, baseWindowStartIdx, baseWindowStartIdx+metadata.windowSize-1, baseWindowMeans[tsAIdx], normalizedValuesA);

            // compute the root of the summed squared normalized values
            rootOfSummedSquaresA = CrossCorrelation.rootOfSummedSquares(normalizedValuesA);

            // iterate over all time series in set B
            for (int tsBIdx = 0; tsBIdx < metadata.setB.size(); tsBIdx++) {

                // iterate over all the lag windows
                for (int lagWindowStartIdx = baseWindowStartIdx + tauMin; lagWindowStartIdx <= baseWindowStartIdx + tauMax; lagWindowStartIdx++) {

                    int tau = lagWindowStartIdx - baseWindowStartIdx;

//                    System.out.println(String.format("Request tsB %s window start %s (hit: %s)",j,lagWindowStartIdx,lagWindowCache.hasWindow(j,lagWindowStartIdx)));
                    normalizedValuesB = lagWindowCache.getNormalizedValues(tsBIdx, lagWindowStartIdx);

//                    double[] expectedNormalizedValuesB = new double[metadata.windowSize];
//                    assert Arrays.equals(expectedNormalizedValuesB, normalizedValuesB) : "normalization caching failed";

                    rootOfSummedSquaresB = lagWindowCache.getRootOfSummedSquares(tsBIdx, lagWindowStartIdx);

                    // compute covariance
                    double covariance = 0; // the enumerator for the pearson product moment correlation
                    for (int t = 0; t < metadata.windowSize; t++)
                        covariance += normalizedValuesA[t] * normalizedValuesB[t];

                    cells[tau-tauMin][tsBIdx] = covariance / rootOfSummedSquaresA / rootOfSummedSquaresB;

                } // lag windows

            } // time series of set B

        } // time series of set A

        return cells;
    }


    /**
     * Computes statistics from the given raw data.
     * @param cells first dimension refers to time lag, second dimension refers to time series
     * @return a column containing the aggregated values
     */
    CorrelationColumn aggregate(double[][] cells, int baseWindowStartIdx, int tauMin) {

        int colLen = cells.length;
        int n = cells[0].length;

        double[] means = new double[colLen],
                sd = new double[colLen],
                median = new double[colLen],
                iqr = new double[colLen],
                posSig = new double[colLen],
                negSig = new double[colLen],
                absSig = new double[colLen];

        for (int i = 0; i < cells.length; i++) {
            DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics(cells[i]);
            means[i] = descriptiveStatistics.getMean();
            sd[i] = descriptiveStatistics.getStandardDeviation();
            median[i] = descriptiveStatistics.getPercentile(50);
            iqr[i] = descriptiveStatistics.getPercentile(75)-descriptiveStatistics.getPercentile(25);

            // if the window size is too small (less than three) significance can't be tested using the t-distribution (see constructor)
            if(significanceTester == null){
                posSig[i] = Double.NaN;
                negSig[i] = Double.NaN;
            } else {
                // test for significance
                int posSigCount = 0, negSigCount = 0;
                for (int j = 0; j < cells[i].length; j++) {
                    if(significanceTester.significanceTest(Math.abs(cells[i][j]))){
                        if(cells[i][j] > 0) posSigCount++;
                        else negSigCount++;
                    }
                }
                posSig[i] = (double) posSigCount / n;
                negSig[i] = (double) negSigCount / n;
                absSig[i] = posSig[i] + negSig[i];
            }

        }

        return new CorrelationColumnBuilder(baseWindowStartIdx,tauMin)
                .negativeSignificant(negSig)
                .positiveSignificant(posSig)
                .absoluteSignificant(absSig)
                .mean(means)
                .standardDeviation(sd)
                .median(median)
                .interquartileRange(iqr)
                .build();

    }

    // -----------------------------------------------------------------------------------------------------------------
    // Stuff
    // -----------------------------------------------------------------------------------------------------------------

    /** @return the number of columns (=windows) in the matrix. */
    public int getSize() { return columns.size(); }

    /** @return the index of the first time series value where the first column (time window) starts. */
    int getStartOffsetInTimeSeries(){
        if(columns.size() == 0) return 0; // the matrix can have no columns if the winodw size exceeds the length of the time series
        return columns.get(0).windowStartIndex;
    }

    /** @return the index of the last time series value of the last column (time window). */
    int getEndOffsetInTimeSeries(){
        if(columns.size() == 0)return 0; // the matrix can have no columns if the winodw size exceeds the length of the time series
        CorrelationColumn lastColumn = columns.get(columns.size()-1);
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

    public void append(CorrelationColumn c) { columns.add(c); }

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
            for(CorrelationColumn c : columns) descriptiveStatistics.addValue(c.getExtremum(STATISTIC, MINMAX));
            extrema[STATISTIC][MINIMUM] = descriptiveStatistics.getMin();
            extrema[STATISTIC][MAXIMUM] = descriptiveStatistics.getMax();
        }
        return extrema[STATISTIC][MINMAX];
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Column Data Structure
    // -----------------------------------------------------------------------------------------------------------------

    public class CorrelationColumn {

        /** Stores the actual column values along all cells. First index refers to the statistic (use {@link #MEAN}, {@link #STD_DEV}, etc. for indexing).
         * Second dimension refers to the time lag. */
        public final double[][] data; // = new double[NUM_STATS][];

        /** Contains the minimal/maximal value of the given statistic along this column.
         * E.g. extrema[STD_DEV][MINIMUM] gives the minimum standard deviation among all time lags for the window represented by this column.
         */
        protected Double[][] extrema = new Double[NUM_STATS][NUM_META_STATS];

        /** Aliases for clearer code. Points to the data stored in the values field.
         * Using the aliases, references to re and im can be avoided. */
        public double[] mean, stdDev, median, positiveSignificant, negativeSignificant;

        /** Each columns represents the results of a window of the x-axis. This is the x-value where the window starts (where it ends follows from the column length). */
        public final int windowStartIndex;

        /** The first value in the column corresponds to this time lag. (Since only complete windows are considered, this deviates for the first columns in the matrix.) */
        public final int tauMin;

        protected CorrelationColumn(CorrelationColumnBuilder builder){
            this.windowStartIndex = builder.windowStartIndex;
            this.tauMin = builder.tauMin;
            data = builder.data;
            mean = data[MEAN]; stdDev = data[STD_DEV]; median = data[MEDIAN]; negativeSignificant = data[POSITIVE_SIGNIFICANT]; positiveSignificant = data[POSITIVE_SIGNIFICANT];
        }

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

        /** @return the number of cells (different time lags) in this column. */
        public int getSize() {
            return data[MEAN].length;
        }

        @Override
        public String toString() {
            return String.format("   mean: %s\nstd dev: %s\n median: %s\nneg sig: %s\npos sig: %s", Arrays.toString(data[MEAN]), Arrays.toString(data[STD_DEV]), Arrays.toString(data[MEDIAN]), Arrays.toString(data[NEGATIVE_SIGNIFICANT]), Arrays.toString(data[POSITIVE_SIGNIFICANT]));
        }
    }

    public class CorrelationColumnBuilder{

        public double[][] data = new double[NUM_STATS][];

        public int windowStartIndex, tauMin;

        public CorrelationColumnBuilder(int windowStartIndex, int tauMin){
            this.windowStartIndex = windowStartIndex;
            this.tauMin = tauMin;
        }

        public CorrelationColumnBuilder mean(double[] mean){
            this.data[MEAN] = mean;
            return this;
        }
        public CorrelationColumnBuilder standardDeviation(double[] standardDeviation){
            this.data[STD_DEV] = standardDeviation;
            return this;
        }
        public CorrelationColumnBuilder median(double[] median){
            this.data[MEDIAN] = median;
            return this;
        }
        public CorrelationColumnBuilder interquartileRange(double[] iqr){
            this.data[IQR] = iqr;
            return this;
        }
        public CorrelationColumnBuilder positiveSignificant(double[] posSig){
            this.data[POSITIVE_SIGNIFICANT] = posSig;
            return this;
        }
        public CorrelationColumnBuilder absoluteSignificant(double[] absSig){
            this.data[ABSOLUTE_SIGNIFICANT] = absSig;
            return this;
        }
        public CorrelationColumnBuilder negativeSignificant(double[] negSig){
            this.data[NEGATIVE_SIGNIFICANT] = negSig;
            return this;
        }

        public CorrelationColumn build(){
            return new CorrelationColumn(this);
        }
    }

    public List<CorrelationColumn> getResultItems() { return columns; }
    public CorrelationColumn getColumn(int columnIndex) { return columns.get(columnIndex); }

    // -----------------------------------------------------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------------------------------------------------

    @Override public String toString(){
        return "Correlation Matrix\n"+ Joiner.on("\n").join(columns);
    }

    /** Reusable concurrent execution logic for computing the matrix. */
    public class ComputeService extends Service<CorrelationMatrix> {

        @Override protected Task<CorrelationMatrix> createTask() {

            Task<CorrelationMatrix> task = new Task<CorrelationMatrix>() {

                @Override protected CorrelationMatrix call() {

                        // TODO: throw new AssertionError("Check whether this code is in sync with the non parallel version.");
                        CorrelationMatrix.this.columns = new ArrayList<>();

                        // caches reusable terms (for base window computations) of the lag windows across all time series of set B
                        LagWindowCache lagWindowCache = new LagWindowCache(metadata, metadata.tauMax-metadata.tauMin+1);
                        System.out.println("Lag cache init size "+metadata.lagRangeOverlap);

                        // all time series in set A and set B are expected to be of equal length
                        int timeSeriesLength = metadata.setA.get(0).getSize();

                        // create the result column by column to avoid having to keep too much data in main memory
                        int totalWork = metadata.numBaseWindows;
                        long timeSpent = 0, rawDataTime = 0, aggregationTime = 0; // number of milliseconds spent computing the matrix
                        for (int baseWindowStartIdx = 0; baseWindowStartIdx <= timeSeriesLength - metadata.windowSize; baseWindowStartIdx += metadata.baseWindowOffset) {

                            long before = System.currentTimeMillis();

                            // predict remaining execution time and update progress
                            int finishedBaseWindows = baseWindowStartIdx/metadata.baseWindowOffset;
                            if(finishedBaseWindows>0){
                                double percentFinished = (double)finishedBaseWindows/totalWork;
                                double totalTime = timeSpent/percentFinished;
                                long time = Math.round(totalTime*(1-percentFinished));
                                long minutes = time / (60 * 1000);
                                long seconds = (time / 1000) % 59 + 1;
                                updateMessage(String.format("Processing base window %s of %s. %d min %02d sec left.",finishedBaseWindows,totalWork, minutes, seconds));
                                updateProgress(finishedBaseWindows, totalWork);
                            }

                            // the first and last columns permit for only some of the time lags
                            int minLagWindowStartIdx = Math.max(0, baseWindowStartIdx + metadata.tauMin);
                            int maxLagWindowStartIdx = Math.min(timeSeriesLength - metadata.windowSize, baseWindowStartIdx + metadata.tauMax);
                            int minTau = minLagWindowStartIdx - baseWindowStartIdx;
                            int maxTau = maxLagWindowStartIdx - baseWindowStartIdx;

                            // compute raw data
                            long b1 = System.currentTimeMillis();
                            double[][] cells = columnCorrelations(baseWindowStartIdx, minTau, maxTau, lagWindowCache);
                            rawDataTime += System.currentTimeMillis()-b1;

                            // aggregate raw data
                            b1 = System.currentTimeMillis();
                            columns.add(aggregate(cells, baseWindowStartIdx, minTau));
                            aggregationTime += System.currentTimeMillis()-b1;

                            timeSpent += System.currentTimeMillis()-before;
                        }

                        CorrelogramStore.append(CorrelationMatrix.this);

                    System.out.println("Raw data computation: "+rawDataTime);
                    System.out.println("Aggregation: "+aggregationTime);

                    return CorrelationMatrix.this;
                }
                @Override protected void cancelled() {
                    super.cancelled();
                    System.out.println("computation aborted.");
                }

                @Override
                protected void failed() {
                    super.failed();
                    updateMessage("Error in matrix computation: " + getException().getMessage());
                    getException().printStackTrace();
                }
            };
            return task;
        }
    }


}
