package Data;

import javax.validation.constraints.NotNull;

/**
 * Represents a series of changing values over time (pairs of X and Y values).
 * @author Carl Witt
 */
public class TimeSeries implements Comparable<TimeSeries>{

    /** One based unique number to access this time series in the data model {@link DataModel}. */
    public final int id;

    /** The coordinates of the points of the time series. 
     * X values are represented as real parts of complex numbers and Y values are represented as imaginary parts of complex numbers. */
    private ComplexSequence values = null;

//    public static int createdInstances = 0;
    
    /**
     * @param values The x and y values, specified in the real and imaginary parts of a complex sequence.
     */
    public TimeSeries(@NotNull ComplexSequence values){
        this.id = -1;
//        this.id = ++createdInstances;
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
        this.id = -1;
        this.values = ComplexSequence.create(new double[d.length], d);
    }

    public int getSize() {
        return values.length;
    }
    
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
        return String.format("Time Series %d\n%s",id,values);
//        return "TimeSeries{" + "id=" + id + ", yValues=" + Arrays.toString(Arrays.copyOfRange(yValues, 0, 3)) + "... }";
    }
    
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + this.id;
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
        
        if(other.id != this.id)
            return false;
        
        return true;
    }

    @Override
    public int compareTo(TimeSeries t) {
        return new Integer(this.id).compareTo(new Integer(t.id));
    }
    
}
