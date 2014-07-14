package Data;

import com.sun.istack.internal.NotNull;

/**
 * Represents a series of (x, y) pairs.
 * The underlying data structure is a {@link Data.ComplexSequence}, see the {@link #values} documentation.
 * The time series class evolved not to add much functionality to the complex sequence it wraps, but it still
 * simplifies access to the somewhat more verbose interface of the complex sequence (e.g. {@link #getMaxX()}).
 * Last but not least, having aliases for the real and imaginary variable names might be less confusing then directly working on a complex sequence.
 *
 * @author Carl Witt
 */
public class TimeSeries implements Comparable<TimeSeries> {

    /** One based unique number to access this time series in the {@link DataModel}. */
    private final int id;

    /** The coordinates of the points of the time series. 
     * X values are represented as real parts of complex numbers and Y values are represented as imaginary parts of complex numbers. */
    private ComplexSequence values = null;
    
    /** To automatically generate ids, decrements of the maximum int value are used. */
//    private static int nextId = 1;
    
    /**
     * @param id
     * @param values The x and y values, specified in the real and imaginary parts of a complex sequence.
     */
    public TimeSeries(int id, @NotNull ComplexSequence values){
        this.id = id;
//        this.id = nextId++;
        this.values = values;
    }
//    public TimeSeries(@NotNull double[] xValues, @NotNull double[] yValues){
//        this(nextId++, xValues, yValues);
//    }

    /** Create time series by specifying only the function values, useful if the x-coordinates don't matter.
     * This will use an efficient way to represent some x-values.
     * Note that the array is taken as is, meaning any changes to the passed array will write through to the time series and vice versa.
     * @param id
     * @param d The sequence of function values
     */
    public TimeSeries(int id, double... d) {
        this.id = id;
        this.values = ComplexSequence.create(new double[d.length], d);
    }

    public TimeSeries(int id, double[] xValues, double[] yValues) {
        this.id = id;
        this.values = ComplexSequence.create(xValues, yValues);
    }

    /** Returns the number of x/y pairs in the time series. */
    public int getSize() { return values.im.length; }
    
    public boolean contains(int id){
        return id >= 0 && id < values.re.length;
    }

    public boolean isEmpty() {
        return this.getSize() == 0;
    }

    public ComplexSequence getDataItems() {
        return values;
    }
    
//    public void append(double item){
//        yValues.add(item);
//        if(item.compareTo(minItem) == -1) minItem = item;
//        if(item.compareTo(maxItem) ==  1) maxItem = item;
//    }

    public double getItemById(int id) {
        return values.im[id];
    }

    public double getMinX() {
        return values.getMin(ComplexSequence.Part.REAL);
    }
    public double getMinY() {
        return values.getMin(ComplexSequence.Part.IMAGINARY);
    }

    public double getMaxX() {
        return values.getMax(ComplexSequence.Part.REAL);
    }
    public double getMaxY() {
        return values.getMax(ComplexSequence.Part.IMAGINARY);
    }
    
    @Override
    public String toString() {
//        return String.format("Time Series %d\n%s",id,values);
        return String.format("Time Series %d", getId());
//        return "TimeSeries{" + "id=" + id + ", yValues=" + Arrays.toString(Arrays.copyOfRange(yValues, 0, 3)) + "... }";
    }
    
    @Override public int hashCode() {
        int hash = 7;
        hash = 79 * hash + this.getId();
        return hash;
    }

    @Override public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TimeSeries other = (TimeSeries) obj;
        
        if(other.getId() != this.getId())
            return false;

        return this.values.equals(other.values);

    }

    @Override public int compareTo(TimeSeries t) {
        return new Integer(this.getId()).compareTo(t.getId());
    }

    public int getId() { return id; }
    
}
