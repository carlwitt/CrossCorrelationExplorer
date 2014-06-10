package Data.Correlation;

import Data.Windowing.WindowMetadata;

import java.util.Arrays;

/**
 * Saves the normalized values and summed squares for a continuous range of k lag windows.
 * This is usefull because usually, two subsequent base windows refer to partially the same lag windows.
 * Internally the cache is implemented as a ring buffer, meaning that adding data for a lag window evicts the data for the oldest entry (with constant cost).
 * This is motivated by the fact that the intersection range between the lag ranges of two subsequent base windows also shifts by baseWindowOffset (the base window offset).
 * Thus, parts of what has been shared between base window k and k+1 can be shared between base window k+1 and k+2 etc, meaning that the shared range is not completely
 * exchanged every time. Instead, some windows are added at the front and some windows drop out at the rear.
 *
 * Consequently window data can be added only at the end of the currently cached range.
 * The windowIndex
 *
 * Example (cache size = 3)
 * store(A); store(B); store(C);
 * rangeStart = A. internal state: (A,B,C)
 * get(0) = A; get(1) = B; get(2) = C;
 * store(D);
 * rangeStart = B. cache internal state: (D,B,C)
 * get(0) = B; get(1) = C; get(2) = D
 * store(E);
 * rangeStart = C. cache internal state: (D,E,C)
 * get(0) = C; get(1) = D; get(2) = E
 */
public class LagWindowCache {

    WindowMetadata metadata;

    /** The index of the time series value where the first buffered lag window starts (first meaning 'having the lowest start index'). */
    private int[] rangeStart; // no valid index at startup

    /** How many windows are currently stored in the cache. This is needed only for the warmup phase where the cache contains less then cacheSize elements. */
    private int[] numberOfElements;

    /** The maximum number of lag windows that can be cached. Should be equal to the overlap of lag ranges but can be smaller if memory is critical. */
    private final int cacheSize;

    /** Points to the array index that is next overwritten. After the cache has been filled completely, this points to the oldest element. */
    private int[] nextWriteLocation;

    /** the dimensions refer to the following: normalizedValues[time series index][lag window index][value index] */
    private final double[][][] normalizedValues;
    /** the dimensions refer to the following: rootOfSummedSquares[time series index][lag window index] */
    private final double[][] rootOfSummedSquares;

    /** Stores the means of the last stored window of a time series. */
    private final double[] frontMeans;
    private final int[] lastFroms;

    public LagWindowCache(WindowMetadata metadata, int cacheSize) {
        this.metadata = metadata;
        this.cacheSize = Math.max(1, cacheSize);

        int numTimeSeries = metadata.setB.size();
        normalizedValues = new double[numTimeSeries][this.cacheSize][];
        rootOfSummedSquares = new double[numTimeSeries][this.cacheSize];

        frontMeans = new double[numTimeSeries];
        Arrays.fill(frontMeans, Double.NaN);
        lastFroms = new int[numTimeSeries];

        rangeStart = new int[numTimeSeries];
        Arrays.fill(rangeStart, -1);

        numberOfElements = new int[numTimeSeries];
        nextWriteLocation = new int[numTimeSeries];
    }

    /**
     * Adds data to the cache.
     * @param timeSeriesIndex the index of the time series to which the window belongs
     * @param startIndex the index of the time series value where the lag window starts.
     *                   This is read only once, when the first element is added. Later on, it is only used to
     *                   - check that the user actually appends windows in a continuous range
     * @param normalizedValues see getNormalizedValues
     * @param rootOfSummedSquares see getRootOfSummedSquares
     */
    protected void put(int timeSeriesIndex, int startIndex, double[] normalizedValues, double rootOfSummedSquares){

        // if the cache is empty, initialize the range
        if(numberOfElements[timeSeriesIndex] == 0) rangeStart[timeSeriesIndex] = startIndex;

        // check the appended window results in a continuous range:
        // rangeEnd must point to the index before the windowStartIndex (works for the empty cache)
        int rangeEnd = rangeStart[timeSeriesIndex] + numberOfElements[timeSeriesIndex] - 1; //
        if(startIndex != rangeEnd+1) throw new AssertionError("Can not append window! " +
                "The window needs to be the subsequent window of the last window in the cached range " +
                "(window start index must be "+(rangeEnd+1)+") but is "+startIndex);

        // store data
        this.normalizedValues[timeSeriesIndex][nextWriteLocation[timeSeriesIndex]] = normalizedValues;
        this.rootOfSummedSquares[timeSeriesIndex][nextWriteLocation[timeSeriesIndex]] = rootOfSummedSquares;

        // the range start moves only, if the cache was full when put was performed. (numberOfElements hasn't been updated here yet!)
        if(numberOfElements[timeSeriesIndex] == cacheSize) rangeStart[timeSeriesIndex]++;
        // the number of elements increases only if the cache is not full yet
        if(numberOfElements[timeSeriesIndex]<cacheSize) numberOfElements[timeSeriesIndex]++;
        // the next write location is (possibly) wrapped around if larger than the last array index
        nextWriteLocation[timeSeriesIndex] = (nextWriteLocation[timeSeriesIndex]+1)%cacheSize;

    }

    /**
     * @param startIndex the index of the time series value where the lag window starts.
     * @return whether the data for the specified window is cached or not. */
    protected boolean hasWindow(int timeSeriesIndex, int startIndex){

        // the last window start index covered
        int rangeEnd = rangeStart[timeSeriesIndex] + numberOfElements[timeSeriesIndex] - 1;
        return startIndex >= rangeStart[timeSeriesIndex] && startIndex <= rangeEnd;

    }

    /**
     * @param startIndex the index of the time series value where the lag window starts.
     * @return an array of the mean-shifted values in that lag window = (x_i - µ) for i in [0, |w|-1]
     */
    public double[] getNormalizedValues(int timeSeriesIndex, int startIndex){

        if(! hasWindow(timeSeriesIndex, startIndex))
            computeWindow(timeSeriesIndex, startIndex);

        // map the request start index to an offset within the cached range
        int rangeOffset = startIndex-rangeStart[timeSeriesIndex];
        // the first element of the cached range is positioned at nextWriteLocation
        int firstRangeElement = numberOfElements[timeSeriesIndex] == cacheSize ? nextWriteLocation[timeSeriesIndex] : 0;
        return normalizedValues[timeSeriesIndex][(firstRangeElement+rangeOffset)%cacheSize];

    }

    /**
     * @param startIndex the index of the time series value where the lag window starts.
     * @return the sum of the squares of the mean-shifted values in that lag window = ∑(x_i - µ)^2
     */
    public double getRootOfSummedSquares(int timeSeriesIndex, int startIndex){

        if(! hasWindow(timeSeriesIndex, startIndex))
            computeWindow(timeSeriesIndex, startIndex);

        // map the request start index to an offset within the cached range
        int rangeOffset = startIndex-rangeStart[timeSeriesIndex];

        // the first element of the cached range is positioned at nextWriteLocation
        int firstRangeElement = numberOfElements[timeSeriesIndex] == cacheSize ? nextWriteLocation[timeSeriesIndex] : 0;
        return rootOfSummedSquares[timeSeriesIndex][(firstRangeElement+rangeOffset)%cacheSize];

    }

    /**
     * Computes the requested window by finding the normalized values and the root of the summed squares for the given time series and window.
     * @param timeSeriesIndex the index of the time series to which the window belongs
     * @param startIndex
     */
    protected void computeWindow(int timeSeriesIndex, int startIndex) {
        final int to = startIndex + metadata.windowSize - 1;
        double mean = CrossCorrelation.incrementalMean(metadata.setB.get(timeSeriesIndex), startIndex, to, frontMeans[timeSeriesIndex], lastFroms[timeSeriesIndex]);
        frontMeans[timeSeriesIndex] = mean;
        lastFroms[timeSeriesIndex] = startIndex;

        double[] normalizedValues = new double[metadata.windowSize];
        CrossCorrelation.normalizeValues(metadata.setB.get(timeSeriesIndex).getDataItems().im, startIndex, to, mean, normalizedValues);
        double rootOfSummedSquares = CrossCorrelation.rootOfSummedSquares(normalizedValues);

        put(timeSeriesIndex, startIndex, normalizedValues, rootOfSummedSquares);
    }
}
