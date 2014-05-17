package Data.Correlation;

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
class LagWindowCache {

    /** The (zero-based) index of the time series value where the first buffered lag window starts (first meaning 'having the lowest start index'). */
    private int rangeStart = -1; // no valid index at startup

    /** How many windows are currently stored in the cache. This is needed only for the warmup phase where the cache contains less then cacheSize elements. */
    private int numberOfElements = 0;

    /** The number of lag windows cached. Should be equal to the overlap of lag ranges but can be smaller if memory is critical. */
    private final int cacheSize;

    /** Points to the array index that is next overwritten. After the cache has been filled completely, this points to the oldest element. */
    private int nextWriteLocation = 0;

    private final double[][] normalizedValues;
    private final double[] summedSquares;

    public LagWindowCache(int cacheSize) {
        this.cacheSize = cacheSize;
        normalizedValues = new double[cacheSize][];
        summedSquares = new double[cacheSize];
    }

    /**
     * Adds data to the cache.
     * @param startIndex the (zero-based) index of the time series value where the lag window starts.
     *                   This is read only once, when the first element is added. Later on, it is only used to
     *                   - check that the user actually appends windows in a continuous range
     * @param normalizedValues see getNormalizedValues
     * @param summedSquares see getSummedSquares
     */
    public void put(int startIndex, double[] normalizedValues, double summedSquares){

        // if the cache is empty, initialize the range
        if(numberOfElements == 0) rangeStart = startIndex;

        // check the appended window results in a continuous range:
        // rangeEnd must point to the index before the windowStartIndex (works for the empty cache)
        int rangeEnd = rangeStart + numberOfElements - 1; //
        if(startIndex != rangeEnd+1) throw new AssertionError("Can not append window! " +
                "The window needs to be the subsequent window of the last window in the cached range " +
                "(window start index must be "+(rangeEnd+1)+" but is "+startIndex);

        // store data
        this.normalizedValues[nextWriteLocation] = normalizedValues;
        this.summedSquares[nextWriteLocation] = summedSquares;

        // the range start moves only, if the cache was full when put was performed. (numberOfElements hasn't been updated here yet!)
        if(numberOfElements == cacheSize) rangeStart++;
        // the number of elements increases only if the cache is not full yet
        if(numberOfElements<cacheSize) numberOfElements++;
        // the next write location is (possibly) wrapped around if larger than the last array index
        nextWriteLocation = (nextWriteLocation+1)%cacheSize;

    }

    /**
     * @param startIndex the (zero-based) index of the time series value where the lag window starts.
     * @return whether the data for the specified window is cached or not. */
    public boolean hasWindow(int startIndex){

        // the last window start index covered
        int rangeEnd = rangeStart + numberOfElements - 1;
        return startIndex >= rangeStart && startIndex <= rangeEnd;

    }

    /**
     * ! the user is responsible for checking the existence of the requested window ! (e.g. use hasWindow)
     * @param startIndex the (zero-based) index of the time series value where the lag window starts.
     * @return an array of the mean-shifted values in that lag window = (x_i - µ) for i in [0, |w|-1]
     */
    public double[] getNormalizedValues(int startIndex){

        // map the request start index to an offset within the cached range
        int rangeOffset = startIndex-rangeStart;
        // the first element of the cached range is positioned at nextWriteLocation
        int firstRangeElement = numberOfElements == cacheSize ? nextWriteLocation : 0;
        return normalizedValues[(firstRangeElement+rangeOffset)%cacheSize];

    }

    /**
     * ! the user is responsible for checking the existence of the requested window ! (e.g. use hasWindow)
     * @param startIndex the (zero-based) index of the time series value where the lag window starts.
     * @return the sum of the squares of the mean-shifted values in that lag window = ∑(x_i - µ)^2
     */
    public double getSummedSquares(int startIndex){

        // map the request start index to an offset within the cached range
        int rangeOffset = startIndex-rangeStart;
        // the first element of the cached range is positioned at nextWriteLocation
        int firstRangeElement = numberOfElements == cacheSize ? nextWriteLocation : 0;
        return summedSquares[(firstRangeElement+rangeOffset)%cacheSize];

    }
}
