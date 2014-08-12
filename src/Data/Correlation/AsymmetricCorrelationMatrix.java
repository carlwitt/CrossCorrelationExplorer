package Data.Correlation;

import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * First version of the correlation matrix. The computation is asymmetric because different rasterings are applied to the time series in input set A and input set B.
 *
 * This implementation is fairly performance optimized, heavily exploiting reusable terms across different windows (see {@link Data.Correlation.LagWindowCache}) and multi-threading.
 *
 * Created by Carl Witt on 11.08.14.
 */

@Deprecated
public class AsymmetricCorrelationMatrix extends CorrelationMatrix {

    public AsymmetricCorrelationMatrix(WindowMetadata metadata) { super(metadata); }

    // -----------------------------------------------------------------------------------------------------------------
    // Computation
    // -----------------------------------------------------------------------------------------------------------------

    protected LagWindowCache lagWindowCache;
    protected double[][] cells;

    /**
     * Resets the columns. Determines a sensible number of threads.
     */
    private void initComputation(){
        columns = new ArrayList<>();
        // partition the input set A among the threads
        int maxThreads = Math.max(metadata.setA.size(), metadata.setB.size());   // each thread gets at least one time series of an input set (the larger input set)
        int minThreads = 1;
        int stdThreads = Runtime.getRuntime().availableProcessors(); // leaving one thread for the system usually gives better results
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
            int stepSizeA = (int) Math.floor((double) metadata.setA.size() / numThreads);   // each thread is assigned at least one time series
            int stepSizeB = (int) Math.floor((double) metadata.setB.size() / numThreads);
            final int fromA = i*stepSizeA,
                    toA   = i == numThreads - 1 ? metadata.setA.size() : (i+1)*stepSizeA;   // last time series A for which this thread is responsible (index is exclusive)
            final int fromB = i*stepSizeB,
                    toB   = i == numThreads - 1 ? metadata.setB.size() : (i+1)*stepSizeB;   // last time series B for which this thread is responsible (index is exclusive)

            System.out.println(String.format("thread %s range for set A [%s,%s[ for set B [%s,%s[", i, fromA, toA, fromB, toB ));
            threads[i] = new Thread(() -> {

                // create the result column by column to avoid having to keep too much data in main memory
                for (int baseWindowStartIdx = 0; baseWindowStartIdx <= timeSeriesLength - metadata.windowSize; baseWindowStartIdx += metadata.baseWindowOffset) {

                    // the first and last columns permit for only some of the time lags
                    int minLagWindowStartIdx = Math.max(0, baseWindowStartIdx + metadata.tauMin);
                    int maxLagWindowStartIdx = Math.min(timeSeriesLength - metadata.windowSize, baseWindowStartIdx + metadata.tauMax);
                    int minTau = minLagWindowStartIdx - baseWindowStartIdx;
                    int maxTau = maxLagWindowStartIdx - baseWindowStartIdx;

                    // precompute the data needed for this base window in parallel
                    for (int tsBIdx = fromB; tsBIdx < toB; tsBIdx++) {
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

            });

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
                baseWindowStartIdx, baseWindowStartIdx + metadata.windowSize - 1,       // from, to
                baseWindowMeans[tsAPlace], baseWindowStartIdx - metadata.baseWindowOffset);  // previous mean, previous from

        // compute the normalized values of the base window
        CrossCorrelation.normalizeValues(tsA.getDataItems().im, baseWindowStartIdx, baseWindowStartIdx + metadata.windowSize - 1, baseWindowMeans[tsAPlace], normalizedValuesA);

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
                    if(significanceTester.significanceTest(cells[lag][j])){
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
                        long seconds = (time / 1000) % 60;
                        updateMessage(String.format("Processing base window %s of %s. %d min %02d sec left.",finishedBaseWindows, totalWork, minutes, seconds));
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

//                    System.out.println(toRMatrix(MEAN));
                    return AsymmetricCorrelationMatrix.this;
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
