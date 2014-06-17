package Data.Correlation;

import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import com.google.common.base.Joiner;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * This class computes a statistics matrix by applying the pearson product-moment correlation to combinations of time series windows.
 */
public class CorrelationMatrix {

    private final static int MINIMUM = 0;
    private final static int MAXIMUM = 1;
    private static final int NUM_META_STATS = 2;                // how many statistics about the statistics are measured (minimum and maximum)

    /** These constants can be used to conveniently refer to certain statistics.
     * {@link #POSITIVE_SIGNIFICANT} percentage of statistically significant (t-test) positive correlation values.
     * {@link #NEGATIVE_SIGNIFICANT} percentage of statistically significant negative correlation values.
     */
    public final static int MEAN = 0, STD_DEV = 1, MEDIAN = 2, IQR = 3, POSITIVE_SIGNIFICANT = 4, NEGATIVE_SIGNIFICANT = 5, ABSOLUTE_SIGNIFICANT = 6;
    public final static int NUM_STATS = 7;                     // how many statistics are measured
    public static final String[] statisticsLabels = new String[]{"mean", "standard deviation", "median", "interquartile range", "% positive significant", "% negative significant", "% significant"};

    public final ComputeService computeService = new ComputeService();

    protected List<CorrelationColumn> columns = new ArrayList<>();

    /** Stores the input of the computation. */
    public final WindowMetadata metadata;

    /** Contains the minimum/maximum value of the given statistic, where the first dimension
     *  refers to the statistic (index using {@link #MEAN}, {@link #STD_DEV}, ...) and the second
     *  dimension specifies the kind of the extremum (index using {@link #MINIMUM}, {@link #MAXIMUM}).
     */
    private final Double[][] extrema = new Double[NUM_STATS][NUM_META_STATS];

    private CorrelationSignificance significanceTester;

    /**
     * Sets the metadata for a correlation matrix, WITHOUT computing the actual contents.
     * @param metadata The input time series, window size, NaN value handling strategy, etc.
     */
    public CorrelationMatrix(WindowMetadata metadata) {

        this.metadata = metadata;

        if(metadata != null && metadata.windowSize > 2){ // for significance testing, we need at least one degree of freedom (degrees of freedom = window size - 2)
            significanceTester = new CorrelationSignificance(metadata.windowSize, getSignificanceLevel(metadata));
//            System.out.println("Init matrix with p = "+metadata.customParameters.get("significanceLevel"));
        }

    }

    public static double getSignificanceLevel(WindowMetadata metadata){
        Double significanceLevel = (Double) metadata.customParameters.get("significanceLevel");
        assert significanceLevel != null : "Please add a significance level to the computation metadata (using CorrelationMatrix.setSignificanceLevel(metadata, pValue) )";
        return significanceLevel;
    }
    public static void setSignificanceLevel(WindowMetadata metadata, double significanceLevel){
        metadata.customParameters.put("significanceLevel", significanceLevel);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Computation
    // -----------------------------------------------------------------------------------------------------------------

    private int numThreads;
    private LagWindowCache lagWindowCache;
    private double[][] cells;

    /**
     * Resets the columns. Determines a sensible number of threads.
     */
    private void initComputation(){
        columns = new ArrayList<>();

        // partition the input set A among the threads
        int maxThreads = Math.max(metadata.setA.size(), metadata.setB.size());   // each thread gets at least one time series of an input set (the larger input set)
        int minThreads = 1;
        int stdThreads = Runtime.getRuntime().availableProcessors() - 1; // leaving one thread for the system usually gives better results
        numThreads = Math.max(minThreads, Math.min(maxThreads, stdThreads));

        // caches reusable terms (for base window computations) of the lag windows across all time series of set B
        int cacheSize = metadata.tauMax - metadata.tauMin + 1;
        lagWindowCache = new LagWindowCache(metadata, cacheSize);

        // the length of a column varies, allocate space accordingly
        cells = new double[metadata.tauMax - metadata.tauMin + 1][metadata.setA.size() * metadata.setB.size()];
    }
    /** Creates a correlation matrix from a list of other time series by first computing the cross correlation for all of them and then computing the average and standard deviation. */
    public void compute(){

        initComputation();

        CyclicBarrier precompute = new CyclicBarrier(numThreads);
        CyclicBarrier compute = new CyclicBarrier(numThreads, new Runnable() {

            // all time series in set A and set B are expected to be of equal length
            final int timeSeriesLength = metadata.setA.get(0).getSize();

            int baseWindowStartIdx = 0;
            @Override public void run() {

                // the first and last columns permit for only some of the time lags
                int minLagWindowStartIdx = Math.max(0, baseWindowStartIdx + metadata.tauMin);
                int maxLagWindowStartIdx = Math.min(timeSeriesLength - metadata.windowSize, baseWindowStartIdx + metadata.tauMax);
                int minTau = minLagWindowStartIdx - baseWindowStartIdx;
                int maxTau = maxLagWindowStartIdx - baseWindowStartIdx;

                columns.add(aggregate(cells, baseWindowStartIdx, minTau, maxTau));

                baseWindowStartIdx += metadata.baseWindowOffset;

            // after having finished a column, aggregate, discard and allocate space for the next
//            cells = new double[maxTau - minTau + 1][metadata.setA.size() * metadata.setB.size()]

        } });

        computeParallel(precompute, compute);

    }

    void computeParallel(final CyclicBarrier precompute, final CyclicBarrier compute) {

        // all time series in set A and set B are expected to be of equal length
        int timeSeriesLength = metadata.setA.get(0).getSize();

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {

            // assign each thread a partition of the input time series sets
            int stepSizeA = (int) Math.ceil((double)metadata.setA.size()/numThreads);   // each thread is assigned at least one time series
            int stepSizeB = (int) Math.ceil((double)metadata.setB.size()/numThreads);
            final int fromA = i*stepSizeA,
                      toA   = i == numThreads - 1 ? metadata.setA.size() : (i+1)*stepSizeA;   // last time series A for which this thread is responsible (index is exclusive)
            final int fromB = i*stepSizeB,
                      toB   = i == numThreads - 1 ? metadata.setB.size() : (i+1)*stepSizeB;   // last time series B for which this thread is responsible (index is exclusive)

            System.out.println(String.format("thread %s range for set A [%s,%s[ for set B [%s,%s[", i, fromA, toA, fromB, toB ));
            threads[i] = new Thread(new Runnable() { @Override public void run() {

                // create the result column by column to avoid having to keep too much data in main memory
                for (int baseWindowStartIdx = 0; baseWindowStartIdx <= timeSeriesLength - metadata.windowSize; baseWindowStartIdx += metadata.baseWindowOffset) {

                    // the first and last columns permit for only some of the time lags
                    int minLagWindowStartIdx = Math.max(0, baseWindowStartIdx + metadata.tauMin);
                    int maxLagWindowStartIdx = Math.min(timeSeriesLength - metadata.windowSize, baseWindowStartIdx + metadata.tauMax);
                    int minTau = minLagWindowStartIdx - baseWindowStartIdx;
                    int maxTau = maxLagWindowStartIdx - baseWindowStartIdx;

                    // precompute the data needed for this base window in parallel
                    for (int tsBIdx = fromB; tsBIdx < Math.min(toB, metadata.setB.size()); tsBIdx++) {
                        precomputeLagWindowData(tsBIdx, baseWindowStartIdx, minTau, maxTau, lagWindowCache);
                    }

                    // wait for all threads having finished precomputation
                    try { precompute.await(); }
                    catch (InterruptedException | BrokenBarrierException e)   { e.printStackTrace(); }

                    // compute raw data
                    for (int tsAPlace = fromA; tsAPlace < Math.min(toA, metadata.setA.size()); tsAPlace++) {
                        columnCorrelations(baseWindowStartIdx, minTau, maxTau, metadata.setA.get(tsAPlace), tsAPlace, lagWindowCache, cells);
                    }

                    // wait for all threads having finished computation. aggregation is performed before threads are released into a next iteration.
                    try { compute.await(); }
                    catch (InterruptedException | BrokenBarrierException e)   { e.printStackTrace(); }

                }

            } });

            threads[i].start();

        }

        // wait until all columns have been computed
        for (int i = 0; i < numThreads; i++) {
            try { threads[i].join(); }
            catch (InterruptedException e) { e.printStackTrace(); }
        }
    }

    void precomputeLagWindowData(int tsBIdx, int baseWindowStartIdx, int tauMin, int tauMax, LagWindowCache lagWindowCache){

        // iterate over all the lag windows
        for (int lagWindowStartIdx = baseWindowStartIdx + tauMin; lagWindowStartIdx <= baseWindowStartIdx + tauMax; lagWindowStartIdx++) {

            if(!lagWindowCache.hasWindow(tsBIdx, lagWindowStartIdx))
                lagWindowCache.computeWindow(tsBIdx, lagWindowStartIdx);

        } // lag windows

    }

    /**
     * Computes all raw correlation values vor a single column of the correlation matrix.
     * @param baseWindowStartIdx the index of the value where the base window starts
     * @param tauMin the minimal valid (resulting in a window of length |w|) time lag
     * @param tauMax the maximum valid (resulting in a window of length |w|) time lag
     * @param tsA the time series to take the base window data from
     * @param tsAPlace the offset of tsA in the input set A (correlationSetA)
     * @param lagWindowCache contains the precomputed data for the lag windows (taken from all time seriese of input set B)
     * @param cells array to store the results to. The first dimension refers to the time lag and must thus be at least of size tauMax-tauMin+1.
     *              The second dimension refers to the pairs of windows (from input set A and B).
     *              The order of the results is (0,0) (0,1) (0,|B|) ... (|A|, 0) ... (|A|, |B|) where |A| is the size of input set A and |B| is the size of input set B.
     *              Using pre-allocated memory for the results of the computation reduces the need for garbage collection.
     */
    void columnCorrelations(int baseWindowStartIdx, int tauMin, int tauMax, TimeSeries tsA, int tsAPlace, LagWindowCache lagWindowCache, double[][] cells) {

//        System.out.println(String.format("baseWindowStartIdx: %s", baseWindowStartIdx));
        // for rolling mean optimization
        double[] baseWindowMeans = new double[metadata.setA.size()];
        Arrays.fill(baseWindowMeans, Double.NaN);

//        double[][] cells = new double[tauMax - tauMin + 1][metadata.setA.size() * metadata.setB.size()];

        // contains the normalized values of the base window of the current time series of set A (each of those base windows is touched only once)
        double[] normalizedValuesA = new double[metadata.windowSize];
        double[] normalizedValuesB; // the same for the current lag window in set B

        // contains the root of the sum of the squared normalized values of the base window of the current time series of set A (each of those base windows is touched only once)
        double rootOfSummedSquaresA;
        double rootOfSummedSquaresB; // the same for the current lag window in set B

        // compute the mean of the base window in an incremental manner, if possible
        baseWindowMeans[tsAPlace] = CrossCorrelation.incrementalMean(tsA,
                baseWindowStartIdx, baseWindowStartIdx+metadata.windowSize-1,       // from, to
                baseWindowMeans[tsAPlace], baseWindowStartIdx-metadata.baseWindowOffset);  // previous mean, previous from

        // compute the normalized values of the base window
        CrossCorrelation.normalizeValues(tsA.getDataItems().im, baseWindowStartIdx, baseWindowStartIdx+metadata.windowSize-1, baseWindowMeans[tsAPlace], normalizedValuesA);

        // compute the root of the summed squared normalized values
        rootOfSummedSquaresA = CrossCorrelation.rootOfSummedSquares(normalizedValuesA);

        // iterate over all time series in set B
        for (int tsBPlace = 0; tsBPlace < metadata.setB.size(); tsBPlace++) {

            // iterate over all the lag windows
            for (int lagWindowStartIdx = baseWindowStartIdx + tauMin; lagWindowStartIdx <= baseWindowStartIdx + tauMax; lagWindowStartIdx++) {

                int tau = lagWindowStartIdx - baseWindowStartIdx;

//                    System.out.println(String.format("[thread %s] Request tsB %s window start %s (hit: %s)",threadId, tsBIdx,lagWindowStartIdx,lagWindowCache.hasWindow(tsBIdx,lagWindowStartIdx)));
//                                final TimeSeries tsB = metadata.setB.get(tsBIdx);
//                                CrossCorrelation.normalizeValues(tsB.getDataItems().im, lagWindowStartIdx, lagWindowStartIdx+metadata.windowSize-1,CrossCorrelation.mean(tsB,lagWindowStartIdx,lagWindowStartIdx+metadata.windowSize-1), normalizedValuesB);
                normalizedValuesB = lagWindowCache.getNormalizedValues(tsBPlace, lagWindowStartIdx);

//                    double[] expectedNormalizedValuesB = new double[metadata.windowSize];
//                    assert Arrays.equals(expectedNormalizedValuesB, normalizedValuesB) : "normalization caching failed";
//                                rootOfSummedSquaresB = CrossCorrelation.rootOfSummedSquares(normalizedValuesB);
                rootOfSummedSquaresB = lagWindowCache.getRootOfSummedSquares(tsBPlace, lagWindowStartIdx);

                // compute covariance
                double covariance = 0; // the enumerator for the pearson product moment correlation
                for (int t = 0; t < metadata.windowSize; t++)
                    covariance += normalizedValuesA[t] * normalizedValuesB[t];

                cells[tau-tauMin][tsAPlace * metadata.setB.size() + tsBPlace] = covariance / rootOfSummedSquaresA / rootOfSummedSquaresB;

            } // lag windows

        } // time series of set B

    }


    /**
     * Computes statistics from the given raw data.
     * @param cells first dimension refers to time lag, second dimension refers to time series
     * @return a column containing the aggregated values
     */
    CorrelationColumn aggregate(double[][] cells, int baseWindowStartIdx, int tauMin, int tauMax) {

        int colLen = tauMax - tauMin + 1;
        int n = cells[0].length;    // the number of correlation values per cell

        double[] means = new double[colLen],
                sd = new double[colLen],
                median = new double[colLen],
                iqr = new double[colLen],
                posSig = new double[colLen],
                negSig = new double[colLen],
                absSig = new double[colLen];

        DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics();
        for (int lag = 0; lag < colLen; lag++) {
            descriptiveStatistics.clear();
            for (int i = 0; i < cells[lag].length; i++) {
                if( ! Double.isNaN(cells[lag][i]))
                    descriptiveStatistics.addValue(cells[lag][i]);
            }
            means[lag] = descriptiveStatistics.getMean();
            sd[lag] = descriptiveStatistics.getStandardDeviation();
            median[lag] = descriptiveStatistics.getPercentile(50);
            double threeQuarters = descriptiveStatistics.getPercentile(75);
            double oneQuarter = descriptiveStatistics.getPercentile(25);
            iqr[lag] = threeQuarters - oneQuarter;
            assert Double.isNaN(iqr[lag]) || iqr[lag] >= 0 : String.format("Negative interquartile range. 75th percentile %s 25th percentile %s \nunsorted data %s \nsorted data %s\nre-evaluation of 75th %s 25th %s",
                    threeQuarters, oneQuarter,
                    Arrays.toString(descriptiveStatistics.getValues()),
                    Arrays.toString(descriptiveStatistics.getSortedValues()),
                    descriptiveStatistics.getPercentile(75), descriptiveStatistics.getPercentile(25)
                    );
            // if the window size is too small (less than three) significance can't be tested using the t-distribution (see constructor)
            if(significanceTester == null){
                posSig[lag] = Double.NaN;
                negSig[lag] = Double.NaN;
            } else {
                // test for significance
                int posSigCount = 0, negSigCount = 0;
                for (int j = 0; j < cells[lag].length; j++) {
                    if(significanceTester.significanceTest(Math.abs(cells[lag][j]))){
                        if(cells[lag][j] > 0) posSigCount++;
                        else negSigCount++;
                    }
                }
                posSig[lag] = (double) posSigCount / n;
                negSig[lag] = (double) negSigCount / n;
                absSig[lag] = posSig[lag] + negSig[lag];
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
            for(CorrelationColumn c : columns) {
                descriptiveStatistics.addValue(c.getExtremum(STATISTIC, MINIMUM));
                descriptiveStatistics.addValue(c.getExtremum(STATISTIC, MAXIMUM));
            }
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
        final Double[][] extrema = new Double[NUM_STATS][NUM_META_STATS];

        /** Aliases for clearer code. Points to the data stored in the values field.
         * Using the aliases, references to re and im can be avoided. */
        public final double[] mean;
        public final double[] stdDev;
        public final double[] median;
        public final double[] positiveSignificant;
        public final double[] negativeSignificant;
        public final double[] absoluteSignificant;

        /** Each columns represents the results of a window of the x-axis. This is the x-value where the window starts (where it ends follows from the column length). */
        public final int windowStartIndex;

        /** The first value in the column corresponds to this time lag. (Since only complete windows are considered, this deviates for the first columns in the matrix.) */
        public final int tauMin;

        CorrelationColumn(CorrelationColumnBuilder builder){
            this.windowStartIndex = builder.windowStartIndex;
            this.tauMin = builder.tauMin;
            data = builder.data;
            mean = data[MEAN];
            stdDev = data[STD_DEV];
            median = data[MEDIAN];
            negativeSignificant = data[NEGATIVE_SIGNIFICANT];
            positiveSignificant = data[POSITIVE_SIGNIFICANT];
            absoluteSignificant = data[ABSOLUTE_SIGNIFICANT];
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
            return String.format("   mean: %s\nstd dev: %s\n median: %s\n    iqr: %s\nneg sig: %s\npos sig: %s\nabs sig: %s", Arrays.toString(data[MEAN]), Arrays.toString(data[STD_DEV]), Arrays.toString(data[MEDIAN]), Arrays.toString(data[IQR]), Arrays.toString(data[NEGATIVE_SIGNIFICANT]), Arrays.toString(data[POSITIVE_SIGNIFICANT]), Arrays.toString(data[ABSOLUTE_SIGNIFICANT]));
        }
    }

    public class CorrelationColumnBuilder{

        public final double[][] data = new double[NUM_STATS][];

        public final int windowStartIndex;
        public final int tauMin;

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

            return new Task<CorrelationMatrix>() {

                long timeSpent = 0,         // total time in the computation loop
                     aggregationTime = 0;   // time spent on aggregating correlation values

                void predictRemainingTime(int baseWindowStartIdx, long elapsedTime){

                    long totalWork = metadata.numBaseWindows;

                    // predict remaining execution time and update progress
                    int finishedBaseWindows = baseWindowStartIdx/metadata.baseWindowOffset;
                    if(finishedBaseWindows>0){
                        double percentFinished = (double)finishedBaseWindows/totalWork;
                        double totalTime = elapsedTime/percentFinished;
                        long time = Math.round(totalTime*(1-percentFinished));
                        long minutes = time / (60 * 1000);
                        long seconds = (time / 1000) % 59 + 1;
                        updateMessage(String.format("Processing base window %s of %s. %d min %02d sec left.",finishedBaseWindows,totalWork, minutes, seconds));
                        updateProgress(finishedBaseWindows, totalWork);
                    }
                }

                @Override protected CorrelationMatrix call() {

                    initComputation();

                    CyclicBarrier precompute = new CyclicBarrier(numThreads);
                    CyclicBarrier compute = new CyclicBarrier(numThreads, new Runnable() {

                        long lastBarrierVisit = System.currentTimeMillis();
                        // the current base window start index is used for computing the remaining time
                        int baseWindowStartIdx = 0;

                        @Override public void run() {

                            // all time series in set A and set B are expected to be of equal length
                            int timeSeriesLength = metadata.setA.get(0).getSize();

                            // the first and last columns permit for only some of the time lags
                            int minLagWindowStartIdx = Math.max(0, baseWindowStartIdx + metadata.tauMin);
                            int maxLagWindowStartIdx = Math.min(timeSeriesLength - metadata.windowSize, baseWindowStartIdx + metadata.tauMax);
                            int minTau = minLagWindowStartIdx - baseWindowStartIdx;
                            int maxTau = maxLagWindowStartIdx - baseWindowStartIdx;

                            // aggregate the correlation distributions and add the column to the result matrix
                            long b1 = System.currentTimeMillis();
                            columns.add(aggregate(cells, baseWindowStartIdx, minTau, maxTau));
                            aggregationTime += System.currentTimeMillis()-b1;

                            baseWindowStartIdx += metadata.baseWindowOffset;

                            // predict and report the remaining time
                            timeSpent += System.currentTimeMillis() - lastBarrierVisit; // this happens once for each column
                            lastBarrierVisit = System.currentTimeMillis();
                            predictRemainingTime(baseWindowStartIdx, timeSpent);        // reports progress to the GUI

                        } });

                    computeParallel(precompute, compute);

                    System.out.println("Raw data computation: "+(timeSpent-aggregationTime));
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
        }
    }


}
