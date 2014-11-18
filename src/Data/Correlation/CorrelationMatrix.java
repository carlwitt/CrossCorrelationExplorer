package Data.Correlation;

import Data.Statistics.CorrelationHistogram;
import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import com.google.common.base.Joiner;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * This class computes a statistics matrix by applying the pearson product-moment correlation to combinations of time series windows.
 */
public class CorrelationMatrix {

    /** Stores the input of the computation. */
    public final WindowMetadata metadata;

    /** The data of the matrix, organized in columns. */
    protected List<CorrelationColumn> columns = new ArrayList<>();

    /** Replaces values that are outside the bounds of time series (when shifting windows too far outside) */
    public final double placeholder = Double.NaN;

    // memory for precomputed reusable terms for cross correlation.
    // having two 2D arrays instead of one 3D array saves one array access per access to the data.
    /** the mean of each window (starting at index 0, 1, 2, ...) of each time series of set A. first dimension refers to time series, second to window. */
    protected double[][] meansA, meansB;
    /** the L2 norm of the mean-shifted window (as a vector) (L2: square root of sum of squared vector entries) */
    protected double[][] L2NormsA, L2NormsB;

    /** These constants can be used to conveniently refer to certain statistics.
     * <pre>
     * {@link #MEAN} the average correlation in a cell.
     * {@link #STD_DEV} the standard deviation of all correlation values within a cell.
     * {@link #MEDIAN} the 50th percentile of all correlation values within a cell.
     * {@link #IQR} the interquartile range (i.e. 75th percentile - 25th percentile) of all correlation values within a cell.
     * {@link #ABSOLUTE_SIGNIFICANT} percentage of statistically significant (by means of a t-test) positive or negative correlation values.
     * {@link #POSITIVE_SIGNIFICANT} percentage of statistically significant positive correlation values.
     * {@link #NEGATIVE_SIGNIFICANT} percentage of statistically significant negative correlation values.
     * </pre>
     */
    public final static int MEAN = 0, STD_DEV = 1, MEDIAN = 2, IQR = 3, POSITIVE_SIGNIFICANT = 4, NEGATIVE_SIGNIFICANT = 5, ABSOLUTE_SIGNIFICANT = 6;
    public final static int NUM_STATS = 7;                     // how many statistics are measured

    /** meta statistic indices for minimum and maximum see {@link #getExtremum(int, int)}  */
    protected final static int MINIMUM = 0, MAXIMUM = 1;
    /** how many statistics about the statistics are measured (minimum and maximum) */
    protected static final int NUM_META_STATS = 2;

    /** Contains the minimum/maximum value of the given statistic, where the first dimension
     *  refers to the statistic (index using {@link #MEAN}, {@link #STD_DEV}, ...) and the second
     *  dimension specifies the kind of the extremum (index using {@link #MINIMUM}, {@link #MAXIMUM}).
     */
    protected final Double[][] extrema = new Double[NUM_STATS][NUM_META_STATS];

    /** Used for t-testing a pearson correlation value on significance. */
    protected CorrelationSignificance significanceTester;

    public final ComputeService computeService = new ComputeService();

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
    public static WindowMetadata setSignificanceLevel(WindowMetadata metadata, double significanceLevel){
        metadata.customParameters.put("significanceLevel", significanceLevel);
        return metadata;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Computation
    // -----------------------------------------------------------------------------------------------------------------

    protected int numThreads = 1;

    /** Resets the column data. Determines a sensible number of threads for parallel computation. */
    private void initComputation(){
        columns = new ArrayList<>();
        // partition the input set A among the threads
        int maxThreads = metadata.numBaseWindows;                   // each thread gets at least one base window
        int minThreads = 1;
        int stdThreads = Runtime.getRuntime().availableProcessors();
        numThreads = Math.max(minThreads, Math.min(maxThreads, stdThreads));
    }

    /** Computes the correlation matrix according to the {@link #metadata} that describes the computation input. */
    public void compute(){

        initComputation();
        computeParallel(null);

    }

    /**
     * Fills the columns data structure. Horizontally partitions the correlation matrix. Each thread is assigned a subsequence of columns to compute.
     * Each partition of the matrix is computed in a {@link Data.Correlation.CorrelationMatrix.PartialMatrixComputer}.
     * @param reportProgress an optional callback to report progress to the GUI. Used by the compute service to pass the current base window index
     *                       which causes prediction of the remaining time and makes the result available via the service reportProgress() etc. methods.
     */
    void computeParallel(Consumer<Integer> reportProgress) {

        // all time series in set A and set B are expected to be of equal length
        precomputeTerms();

        List<Callable<CorrelationMatrix>> threads = new ArrayList<>(numThreads);
        for (int i = 0; i < numThreads; i++) {

            // assign each thread a partition of the base windows to process
            int stepSize = (int) Math.floor((double) metadata.numBaseWindows / numThreads);      // there won't be more threads than windows {@link initComputation()}
            final int from = i*stepSize,
                      to   = i == numThreads - 1 ? metadata.numBaseWindows : (i+1)*stepSize;     // index is exclusive

//            if(RuntimeConfiguration.VERBOSE) System.out.println(String.format("thread %s is responsible for base window range [%s,%s[", i, from, to));

            // create a thread handling the given bounds. pass the progress reporter callback only to the first.
            threads.add(new PartialMatrixComputer(from, to, i == numThreads-1 ? reportProgress : null));

        }

        final ExecutorService service = Executors.newFixedThreadPool(numThreads);
        try {
            List<Future<CorrelationMatrix>> results = service.invokeAll(threads);
            // append columns to the matrix in order of computation
            for(Future<CorrelationMatrix> f : results) f.get().columns.forEach(this::append);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println(String.format("Computation aborted. Shutting down execution pool."));
            service.shutdownNow();
            e.printStackTrace();
        }
        service.shutdown();

    }


    /**
     * TODO: Is precomputation for large time lag steps slower than no precomputation?
     * Computes the means and standard deviations for each window necessary for the cc matrix computation.
     * This induces a slight overhead in the main computation, since the values in each window need to be normalized to
     * compute the L2 norm.
     */
    protected void precomputeTerms() {

        // number of possible placements of a window: number of data points
        int numWindows = metadata.setA.get(0).getSize();
        int lastValidWindowStartIdx = metadata.setA.get(0).getSize() - metadata.windowSize;

        meansA = new double[metadata.setA.size()][numWindows];
        meansB = new double[metadata.setB.size()][numWindows];
        L2NormsA = new double[metadata.setA.size()][numWindows];
        L2NormsB = new double[metadata.setB.size()][numWindows];

        // precompute means for time series ensemble A
        for (int tsIdx = 0; tsIdx < metadata.setA.size(); tsIdx++) {
            TimeSeries tsA = metadata.setA.get(tsIdx);
            Arrays.fill(meansA[tsIdx], placeholder);
            meansA[tsIdx][0] = CrossCorrelation.mean(tsA, 0, metadata.windowSize - 1);
            for (int offset = 1; offset <= lastValidWindowStartIdx; offset++) {
                meansA[tsIdx][offset] = CrossCorrelation.incrementalMean(tsA, offset, offset+metadata.windowSize-1, meansA[tsIdx][offset-1], offset-1);
            }
        }

        // precompute means for time series ensemble B
        for (int tsIdx = 0; tsIdx < metadata.setB.size(); tsIdx++) {
            TimeSeries tsB = metadata.setB.get(tsIdx);
            Arrays.fill(meansB[tsIdx], placeholder);
            meansB[tsIdx][0] = CrossCorrelation.mean(tsB, 0, metadata.windowSize-1);
            for (int offset = 1; offset <= lastValidWindowStartIdx; offset++) {
                meansB[tsIdx][offset] = CrossCorrelation.incrementalMean(tsB, offset, offset+metadata.windowSize-1, meansB[tsIdx][offset-1], offset-1);
            }
        }

        double[] normalizedValues = new double[metadata.windowSize];

        // precompute L2 norms of normalized window data for time series A
        for (int tsIdx = 0; tsIdx < metadata.setA.size(); tsIdx++) {
            TimeSeries tsA = metadata.setA.get(tsIdx);
            Arrays.fill(L2NormsA[tsIdx], placeholder);
            double[] data = tsA.getDataItems().im;
            for (int offset = 0; offset <= lastValidWindowStartIdx; offset++) {
                CrossCorrelation.normalizeValues(data, offset, offset+metadata.windowSize-1, meansA[tsIdx][offset], normalizedValues);
                L2NormsA[tsIdx][offset] = CrossCorrelation.rootOfSummedSquares(normalizedValues);
            }
        }

        // precompute L2 norms of normalized window data for time series B
        for (int tsIdx = 0; tsIdx < metadata.setB.size(); tsIdx++) {
            TimeSeries tsB = metadata.setB.get(tsIdx);
            Arrays.fill(L2NormsB[tsIdx], placeholder);
            double[] data = tsB.getDataItems().im;
            for (int offset = 0; offset <= lastValidWindowStartIdx; offset++) {
                CrossCorrelation.normalizeValues(data, offset, offset+metadata.windowSize-1, meansB[tsIdx][offset], normalizedValues);
                L2NormsB[tsIdx][offset] = CrossCorrelation.rootOfSummedSquares(normalizedValues);
            }
        }

    }

    /** Computes one horizontal slice of the correlation matrix, that is, all columns in a given range. */
    private class PartialMatrixComputer implements Callable<CorrelationMatrix>{

        CorrelationMatrix partialMatrix = new CorrelationMatrix(metadata); // each threads own results (a subsequence of the matrixs columns)

        /** The first window index (inclusive) and the last window index (exclusive). */
        final int from, to;
        final Consumer<Integer> progress;

        private PartialMatrixComputer(int from, int to, Consumer<Integer> progress) {
            this.from = from;
            this.to = to;
            this.progress = progress;
        }

        @Override public CorrelationMatrix call() throws Exception {

            int columnSize = metadata.getNumberOfDifferentTimeLags(),
                valuesPerCell = metadata.setA.size() * metadata.setB.size();

            DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics();

            int windowAStartIdx, windowBStartIdx;                       // the offset of the current window pair
            double[] windowAData = new double[metadata.windowSize],     // values in the current window pair
                     windowBData = new double[metadata.windowSize];
            double   windowAMean, windowAL2Norm,                        // means and L2 norms of the current window pair
                     windowBMean, windowBL2Norm;

            boolean isProgressReporter = progress != null; // only one thread reports its progress (it is assumed that all threads proceed at the same speed)

            // create the result column by column to avoid having to keep too much data in main memory
            for (int baseWindowIdx = from; baseWindowIdx < to; baseWindowIdx++) {

                if(isProgressReporter) progress.accept(baseWindowIdx-from);

                int baseWindowStartIdx = metadata.baseWindowOffset * baseWindowIdx;

                CorrelationHistogram correlationHistogram = new CorrelationHistogram(metadata);
                CorrelationColumn column = new CorrelationColumnBuilder(baseWindowStartIdx, metadata.tauMin).allEmpty(columnSize).histogram(correlationHistogram).build();

                // compute columns cell by cell
                int lagIdx = 0;
                for(int lag : metadata.getDifferentTimeLags()){

                    if(Thread.currentThread().isInterrupted())
                        return null;

                    descriptiveStatistics.clear();

                    if (lag >= 0) {
                        // process positive time lags (look at past events in time series B ~ find influences of B on A)
                        windowAStartIdx = baseWindowStartIdx;
                        windowBStartIdx = baseWindowStartIdx - lag;
                    } else {
                        // process negative time lags (look for occurrences of a pattern in time series B in the past of time series A ~ find influences of A on B)
                        windowAStartIdx = baseWindowStartIdx + lag;
                        windowBStartIdx = baseWindowStartIdx;
                    }

                    // compute all pairwise correlation values
                    List<TimeSeries> setA = metadata.setA;
                    for (int tsAIdx = 0; tsAIdx < setA.size(); tsAIdx++) {
                        TimeSeries tsA = setA.get(tsAIdx);

                        CrossCorrelation.getWindow(windowAData, tsA, windowAStartIdx, placeholder);

                        List<TimeSeries> setB = metadata.setB;
                        for (int tsBIdx = 0; tsBIdx < setB.size(); tsBIdx++) {
                            TimeSeries tsB = setB.get(tsBIdx);

                            CrossCorrelation.getWindow(windowBData, tsB, windowBStartIdx, placeholder);

                            if(windowAStartIdx < 0){
                                windowAMean = Double.NaN;
                                windowAL2Norm = Double.NaN;
                            } else {
                                windowAMean = meansA[tsAIdx][windowAStartIdx];
                                windowAL2Norm = L2NormsA[tsAIdx][windowAStartIdx];
                            }

                            if(windowBStartIdx < 0){
                                windowBMean = Double.NaN;
                                windowBL2Norm = Double.NaN;
                            } else {
                                windowBMean = meansB[tsBIdx][windowBStartIdx];
                                windowBL2Norm = L2NormsB[tsBIdx][windowBStartIdx];
                            }

                            double covariance = 0;
                            for (int i = 0; i < metadata.windowSize; i++)
                                covariance += (windowAData[i] - windowAMean) * (windowBData[i] - windowBMean);
                            double r = covariance / windowAL2Norm / windowBL2Norm;

                            if (!Double.isNaN(r)) descriptiveStatistics.addValue(r);

                        } // for each time series in set B

                    } // for each time series in set A

                    // summarize the computed distribution (calculate mean, sd, etc) and store the results in the column data structure
                    column.computeCell(descriptiveStatistics, valuesPerCell, lagIdx);
                    correlationHistogram.setDistribution(lagIdx, descriptiveStatistics.getValues());
                    lagIdx++;

                } // for each lag

                partialMatrix.append(column);

            }

            return partialMatrix;
        }

    }

    /**
     * Computes all correlation values for a given window index and lag index.
     * @param baseWindowIdx the x coordinate of the cell, in cell coordinates (see {@link Visualization.Correlogram}).
     * @param lagIndex the y coordinate of the cell, in cell coordinates.
     * @return all correlation values (including NaNs, if present) between the windows of the time series in the two input sets.
     */
    public double[] computeSingleCell(int baseWindowIdx, int lagIndex){

        int lag = metadata.tauMin + lagIndex * metadata.tauStep;

        double[] windowAData = new double[metadata.windowSize],
                 windowBData = new double[metadata.windowSize];

        double[] result = new double[metadata.setA.size() * metadata.setB.size()];

        int baseWindowStartIdx = metadata.baseWindowOffset * baseWindowIdx;

        int windowAStartIdx, windowBStartIdx;
        if (lag < 0) {       // process negative time lags (shift time series A to the right ~ find influences of B on A)
            windowAStartIdx = baseWindowStartIdx + lag;
            windowBStartIdx = baseWindowStartIdx;
        } else {             // process positive time lags (shift time series B to the right ~ find influences of A on B)
            windowAStartIdx = baseWindowStartIdx;
            windowBStartIdx = baseWindowStartIdx - lag;
        }

        int rCounter = 0;
        for (TimeSeries tsA : metadata.setA) {
            CrossCorrelation.getWindow(windowAData, tsA, windowAStartIdx, placeholder);

            for (TimeSeries tsB : metadata.setB) {
                CrossCorrelation.getWindow(windowBData, tsB, windowBStartIdx, placeholder);
                result[rCounter++] = CrossCorrelation.correlationCoefficient(windowAData, windowBData);
            }

        }

        return result;
    }

    /** Reusable concurrent execution logic for computing the matrix. */
    public class ComputeService extends Service<CorrelationMatrix> {

        @Override protected Task<CorrelationMatrix> createTask() {

            return new Task<CorrelationMatrix>() {

                long timeSpent = 0,         // total time in the computation loop
                     aggregationTime = 0;   // time spent on aggregating correlation values

                int totalWork = metadata.numBaseWindows;
                long computationStart = System.currentTimeMillis(),  // when the last base window was finished
                     elapsedTime = 0;

                void predictRemainingTime(int finishedBaseWindows){

                    elapsedTime = System.currentTimeMillis() - computationStart; // this happens once for each base window

                    // predict remaining execution time and update progress
                    if(finishedBaseWindows>0){
                        // the total number of finished windows is estimated to be numThreads times larger than the progress the first worker has made so far.
                        double percentFinished = (double)numThreads*finishedBaseWindows/totalWork;
                        double estimatedTotalTime = elapsedTime/percentFinished;
                        long remainingTime = Math.round(estimatedTotalTime*(1-percentFinished));
                        long minutes = remainingTime / (60 * 1000);
                        long seconds = (remainingTime / 1000) % 60;
                        updateMessage(String.format("Processing base window %s of %s. %d min %02d sec left.",numThreads*finishedBaseWindows, totalWork, minutes, seconds));
                        updateProgress(numThreads*finishedBaseWindows, totalWork);
                    }
                }

                @Override protected CorrelationMatrix call() {
                    initComputation();
                    updateMessage("Precomputing data.");
                    computeParallel(this::predictRemainingTime);
//                    System.out.println("Raw data computation: "+(timeSpent-aggregationTime));
//                    System.out.println("Aggregation: "+aggregationTime);
                    return CorrelationMatrix.this;
                }
                @Override protected void cancelled() {
                    super.cancelled();
                    System.out.println("Computation aborted.");
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

    // -----------------------------------------------------------------------------------------------------------------
    // DATA TYPES
    // -----------------------------------------------------------------------------------------------------------------

    // -----------------------------------------------------------------------------------------------------------------
    // Column Data Structure
    // -----------------------------------------------------------------------------------------------------------------

    public class CorrelationColumn {

        /** Stores the actual column values along all cells. First index refers to the statistic (use {@link #MEAN}, {@link #STD_DEV}, etc. for indexing).
         * Second dimension refers to the time lag index. */
        public final double[][] data; // = new double[NUM_STATS][];

        public CorrelationHistogram histogram;

        public final static short histogramResolution = 180; // bins, covering the possible range between -1 and 1

        /** Contains the minimal/maximal value of the given statistic along this column.
         * E.g. extrema[STD_DEV][MINIMUM] gives the minimum standard deviation among all time lags for the window represented by this column. */
        final Double[][] extrema = new Double[NUM_STATS][NUM_META_STATS];

        /** Each columns represents the results of a window of the x-axis. This is the x-value where the window starts (where it ends follows from the column length). */
        public final int windowStartIndex;

        /** The first value in the column corresponds to this time lag. (Since only complete windows are considered, this deviates for the first columns in the matrix.) */
        public final int tauMin;

        CorrelationColumn(CorrelationColumnBuilder builder){
            this.windowStartIndex = builder.windowStartIndex;
            this.tauMin = builder.tauMin;
            this.histogram = builder.histogram;
            data = builder.data;
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

        /**
         * Computes and sets the statistics for the cell corresponding to a certain time lag index.
         * @param descriptiveStatistics the correlation values (excluding NaNs)
         * @param numValues the number of correlation values (including NaNs)
         * @param lagIdx the offset of the cell in the column (0 corresponds to the minimum time lag)
         */
        public void computeCell(DescriptiveStatistics descriptiveStatistics, int numValues, int lagIdx) {

            data[MEAN][lagIdx] = descriptiveStatistics.getMean();
            data[STD_DEV][lagIdx] = Math.sqrt(descriptiveStatistics.getPopulationVariance());
            data[MEDIAN][lagIdx] = descriptiveStatistics.getPercentile(50);
            data[IQR][lagIdx] = descriptiveStatistics.getPercentile(75) - descriptiveStatistics.getPercentile(25);

            // if the window size is too small (less than three) significance can't be tested using the t-distribution.
            if(significanceTester == null){
                data[POSITIVE_SIGNIFICANT][lagIdx] = Double.NaN;
                data[NEGATIVE_SIGNIFICANT][lagIdx] = Double.NaN;
                data[ABSOLUTE_SIGNIFICANT][lagIdx] = Double.NaN;
            } else {
                // test for significance
                int posSigCount = 0, negSigCount = 0;
                for (double r : descriptiveStatistics.getValues()) {
                    if(significanceTester.significanceTest(r)){
                        if(r > 0) posSigCount++;
                        else negSigCount++;
                    }
                }
                data[POSITIVE_SIGNIFICANT][lagIdx] = (double) posSigCount / numValues;
                data[NEGATIVE_SIGNIFICANT][lagIdx] = (double) negSigCount / numValues;
                data[ABSOLUTE_SIGNIFICANT][lagIdx] = data[POSITIVE_SIGNIFICANT][lagIdx] + data[NEGATIVE_SIGNIFICANT][lagIdx];
            }

        }

        /** @return the number of cells (different time lags) in this column. */
        public int getSize() {
            return data[MEAN].length;
        }

        @Override
        public String toString() {
            return String.format("   mean: %s\nstd dev: %s\n median: %s\n    iqr: %s\nneg sig: %s\npos sig: %s\nabs sig: %s", Arrays.toString(data[MEAN]), Arrays.toString(data[STD_DEV]), Arrays.toString(data[MEDIAN]), Arrays.toString(data[IQR]), Arrays.toString(data[NEGATIVE_SIGNIFICANT]), Arrays.toString(data[POSITIVE_SIGNIFICANT]), Arrays.toString(data[ABSOLUTE_SIGNIFICANT]));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CorrelationColumn column = (CorrelationColumn) o;

            if (tauMin != column.tauMin) return false;
            if (windowStartIndex != column.windowStartIndex) return false;
            for (int STAT = 0; STAT < CorrelationMatrix.NUM_STATS; STAT++) {
                if( ! Arrays.equals(data[STAT], column.data[STAT]) ) return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = windowStartIndex;
            result = 31 * result + tauMin;
            return result;
        }
    }

    public class CorrelationColumnBuilder{

        public final double[][] data = new double[NUM_STATS][];

        public final int windowStartIndex;
        public final int tauMin;
        public CorrelationHistogram histogram = null;

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
        public CorrelationColumnBuilder histogram(CorrelationHistogram histogram){
            this.histogram = histogram;
            return this;
        }
        public CorrelationColumnBuilder allEmpty(int columnSize) {
            this.data[MEAN] = new double[columnSize];
            this.data[STD_DEV] = new double[columnSize];
            this.data[MEDIAN] = new double[columnSize];
            this.data[IQR] = new double[columnSize];
            this.data[POSITIVE_SIGNIFICANT] = new double[columnSize];
            this.data[ABSOLUTE_SIGNIFICANT] = new double[columnSize];
            this.data[NEGATIVE_SIGNIFICANT] = new double[columnSize];
            return this;
        }

        public CorrelationColumn build(){
            return new CorrelationColumn(this);
        }
    }

    public List<CorrelationColumn> getColumns() { return columns; }
    public CorrelationColumn getColumn(int columnIndex) { return columns.get(columnIndex); }


    // -----------------------------------------------------------------------------------------------------------------
    // Stuff
    // -----------------------------------------------------------------------------------------------------------------

    /** @return the number of columns (=windows) in the matrix. */
    public int getSize() {
        assert metadata.numBaseWindows == columns.size() : String.format("Number of base windows (%s) differs from the number of columns (%s)", metadata.numBaseWindows, columns.size());
        return columns.size();
    }

    public int getLastFilledColumnIndex(int STATISTIC){
        // scan columns from back
        for (int colIdx = columns.size()-1; colIdx >= 0; colIdx--) {
            CorrelationColumn correlationColumn = columns.get(colIdx);
            for (int rowIdx = 0; rowIdx < correlationColumn.getSize(); rowIdx++) {
                // if column has a non nan value, this is the highest index column with a non nan value
                if(! Double.isNaN(correlationColumn.data[STATISTIC][rowIdx])){
                    return colIdx;
                }
            }
        }
        return -1;
    }

    public void append(CorrelationColumn c) { columns.add(c); }

    public WindowMetadata getMetadata(){ return metadata; }

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

    /**
     * @return whether the given integer refers to an existing statistic, e.g. is one of {@link #MEAN}, {@link #MEDIAN}, etc.
     */
    public static boolean isValidStatistic(int SOURCE_STATISTIC){
        return SOURCE_STATISTIC >= 0 && SOURCE_STATISTIC < NUM_STATS;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------------------------------------------------

    @Override public String toString(){
        return "Correlation Matrix\n"+ Joiner.on("\n\n").join(columns);
    }

    public String toRMatrix(int STATISTIC){

        StringBuilder buffer = new StringBuilder();
        int rows = metadata.tauMax - metadata.tauMin + 1;
        for (int i = 0; i < rows; i++) {
            for (CorrelationColumn column : columns){
                if(i < column.data[STATISTIC].length)
                    buffer.append(String.format("%.3f",column.data[STATISTIC][i])).append(",");
                else
                    buffer.append("-0,");
            }

        }
        buffer.deleteCharAt(buffer.length()-1);// remove trailing comma
        return String.format("matrix(ncol=%s, nrow=%s, data=c(%s))",columns.size(), rows, buffer.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CorrelationMatrix that = (CorrelationMatrix) o;

        if (columns != null ? !columns.equals(that.columns) : that.columns != null) return false;
        if (metadata != null ? !metadata.equals(that.metadata) : that.metadata != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = columns != null ? columns.hashCode() : 0;
        result = 31 * result + (metadata != null ? metadata.hashCode() : 0);
        return result;
    }
}
