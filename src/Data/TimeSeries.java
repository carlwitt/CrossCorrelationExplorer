package Data;

import com.sun.istack.internal.NotNull;

/**
 * Represents a series of changing values over time (pairs of X and Y values).
 * @author Carl Witt
 */
public class TimeSeries implements Comparable<TimeSeries> {

    /** One based unique number to access this time series in the data model {@link DataModel}. */
    protected int id;

    /** The coordinates of the points of the time series. 
     * X values are represented as real parts of complex numbers and Y values are represented as imaginary parts of complex numbers. */
    private ComplexSequence values = null;
    
    /** To automatically generate ids, decrements of the maximum int value are used. */
    private static int nextId = Integer.MAX_VALUE;
    
    /**
     * @param values The x and y values, specified in the real and imaginary parts of a complex sequence.
     */
    public TimeSeries(@NotNull ComplexSequence values){
        this.id = nextId--;
        this.values = values;
    }
    public TimeSeries(int id, @NotNull double[] xValues, @NotNull double[] yValues){
        this.id = id;
        this.values = ComplexSequence.create(xValues, yValues);
    }
    
    /** Create time series by specifying only the function values, useful if the x-coordinates don't matter.
     * This will use an efficient way to represent some x-values.
     * Note that the array is taken as is, meaning any changes to the passed array will write through to the time series and vice versa.
     * @param d The sequence of function values
     */
    public TimeSeries(double[] d) {
        this.id = nextId--;
        this.values = ComplexSequence.create(new double[d.length], d);
    }

    /** Returns the number of x/y pairs in the time series. */
    public int getSize() { return values.length; }
    
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

    public double getMinItem() {
        return values.getMin(ComplexSequence.Part.IMAGINARY);
    }

    public double getMaxItem() {
        return values.getMax(ComplexSequence.Part.IMAGINARY);
    }
    
    @Override
    public String toString() {
//        return String.format("Time Series %d\n%s",id,values);
        return String.format("Time Series %d", getId());
//        return "TimeSeries{" + "id=" + id + ", yValues=" + Arrays.toString(Arrays.copyOfRange(yValues, 0, 3)) + "... }";
    }
    
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + this.getId();
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
        final TimeSeries other = (TimeSeries) obj;
        
        if(other.getId() != this.getId())
            return false;

        return this.values.equals(other.values);

    }

    /** Like equals, but without taking the ID into consideration. */
    public boolean equivalent(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TimeSeries other = (TimeSeries) obj;

        return true;
    }

    @Override
    public int compareTo(TimeSeries t) {
        return new Integer(this.getId()).compareTo(new Integer(t.getId()));
    }

    public int getId() { return id; }
    
}
