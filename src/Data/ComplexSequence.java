package Data;

import com.sun.istack.internal.NotNull;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Represents a fixed length sequence of complex numbers (consisting of a real and an imaginary part).
 * It is used to represent time series (x and y values) and aggregated correlation results (mean and standard deviation).
 * @author Carl Witt
 */
public class ComplexSequence implements List<Double> {

    /** The precision used to check if two complex sequences are the same. */
    public static final double EQ_COMPARISON_THRESHOLD = 1E-6;
    
    public static enum Part{REAL, IMAGINARY};
    
    /** Real and imaginary parts. */
    public double[] re;
    public double[] im;
    
    public final int length;
    private double[] realMinMax;
    private double[] imagMinMax; 
    
    /**
     * @param c Input data
     * @param left Start of window (inclusive)
     * @param right End of window (inclusive)
     * @return A copy of the input data in the specified window
     */
    public static ComplexSequence create(@NotNull ComplexSequence c, int left, int right) {
        double[] re = c.re == null ? new double[right-left+1] : Arrays.copyOfRange(c.re, left, right+1);
        double[] im = c.im == null ? new double[right-left+1] : Arrays.copyOfRange(c.im, left, right+1);
        return new ComplexSequence(re, im);
    }
    /**
     * @param realValues The real parts of the sequence of complex numbers.
     */
    public static ComplexSequence create(@NotNull double[] realValues) {
        return new ComplexSequence(realValues, new double[realValues.length]);
    }
    public static ComplexSequence create(@NotNull double[] realValues, @NotNull double[] imaginaryValues){
        if(imaginaryValues != null && realValues.length != imaginaryValues.length)
            System.err.println("Count of real values doesn't match count of imaginary values in ComplexSequence construction.");
        return new ComplexSequence(realValues, imaginaryValues);
    }
    /**
     * Copy constructor, more convenient than Object.clone() since no typecast will be necessary.
     */
    public static ComplexSequence create(@NotNull ComplexSequence c){
        return new ComplexSequence(Arrays.copyOf(c.re, c.length), Arrays.copyOf(c.im, c.length));
    }
    
    private ComplexSequence(double[] realValues, double[] imaginaryValues){
        this.re = realValues;
        this.im = imaginaryValues;
        this.length = imaginaryValues.length;
    }
    
    public static double[] calcMinMax(double[] array){
        
        if(array.length == 0)
            return new double[]{Double.NaN, Double.NaN};
        
        double min = Double.isNaN(array[0]) ? Double.POSITIVE_INFINITY : array[0];
        double max = Double.isNaN(array[0]) ? Double.NEGATIVE_INFINITY : array[0];
        
        // the number of unchecked re is re.length - 1
        int pairs = (array.length - 1) / 2;    // rounds down, which is important

        // checking in pairs gives 3 comparisons per 2 elements, which is 25% 
        // less comparisons than checking every value against both min and max
        for (int i = 1; i <= pairs; i++) {
            double first = array[i*2-1];   // first pair: 1,2 next pair: 3,4
            double secnd = array[i*2];
            if(first < secnd){
                min = Double.isNaN(first) ? min : Math.min(min, first);
                max = Double.isNaN(secnd) ? max : Math.max(max, secnd);
            } else {
                min = Double.isNaN(secnd) ? min : Math.min(min, secnd);
                max = Double.isNaN(first) ? max : Math.max(max, first);
            }
        }
        
        // if there's one element trailing at the end of the sequence, check it
        if(2*pairs + 1 < array.length){
            min = Double.isNaN(array[pairs*2+1]) ? min : Math.min(min, array[pairs*2+1]);
            max = Double.isNaN(array[pairs*2+1]) ? max : Math.max(max, array[pairs*2+1]);
        }
        
        return new double[]{min, max};
    }
    
    private void multiplyAll(double[] array, double factor){
        for (int i = 0; i < array.length; i++) {
            array[i] = factor * array[i];
        }
    }
    // negates all complex parts
    public ComplexSequence conjugate(){
        multiplyAll(im,-1);
        return this;
    }
//    public ComplexSequence conjugateReal() {
//        multiplyAll(re,-1);
//        return this;
//    }
    
    
    public static ComplexSequence conjugate(ComplexSequence a){
        ComplexSequence result = ComplexSequence.create(Arrays.copyOf(a.re, a.size()), Arrays.copyOf(a.im, a.size()));
        result.conjugate();
        return result;
    }
    
    /** Multiplies the sequence point by point with another sequence. This manipulates the sequence. */
    public ComplexSequence pointWiseProduct(ComplexSequence other) {
        if(size() != other.size()){
            System.err.println("Point wise product requires two complex sequences of the same length.");}
        for (int i = 0; i < other.size(); i++) {
            double newR = re[i]*other.re[i] - im[i]*other.im[i];
            double newI = im[i]*other.re[i] + re[i]*other.im[i];
            re[i] = newR;
            im[i] = newI;
        }
        return this;
    }
    
    /** Modifies all values to have at most precision of some specified number of decimals.
     * This modifies the sequence.
     */
    public ComplexSequence roundPrecision(int decimals){
        double base = Math.pow(10, decimals);
        for (int i = 0; i < re.length; i++) {
            re[i] = Math.round(re[i] * base) / base;
            im[i] = Math.round(im[i] * base) / base;
        }
        return this;
    }
    public double getMin(Part part){
        if(part == Part.REAL){
            if(realMinMax == null) 
                realMinMax = calcMinMax(re);
            return realMinMax[0];
        } else {
            if(imagMinMax == null) 
                imagMinMax = calcMinMax(im);
            return imagMinMax[0];
        }
    }
    public double getMax(Part part){
        if(part == Part.REAL){
             if(realMinMax == null) 
                realMinMax = calcMinMax(re);
            return realMinMax[1];
        } else {
            if(imagMinMax == null) 
                imagMinMax = calcMinMax(im);
            return imagMinMax[1];
        }
    }

    @Override
    public int size() {
        return im.length;
    }

    @Override
    public boolean isEmpty() {
        return re.length == 0;
    }

    @Override
    public Double get(int i) {
        return re[i];
    }

    public String toString(){
        String result = "ComplexSequence: ";
        for (int i = 0; i < re.length-1; i++) {
            result += String.format("%s%s%si, ",re[i],im[i]>=0?"+":"",im[i]); 
        }
        result += String.format("%s%s%si",re[re.length-1],im[re.length-1]>=0?"+":"",im[re.length-1]); 
        return result;
     }
    public String toString(int decimals){
        ComplexSequence temp = ComplexSequence.create(this);
        temp.roundPrecision(decimals);
        return "re: " + Arrays.toString(temp.re) + "\nim: " + Arrays.toString(temp.im);
     }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Arrays.hashCode(this.re);
        hash = 37 * hash + Arrays.hashCode(this.im);
        hash = 37 * hash + this.length;
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
        final ComplexSequence other = (ComplexSequence) obj;
        if(other.re.length != re.length) return false;
        for (int i = 0; i < re.length; i++)
            if( Math.abs(other.re[i]-re[i]) > EQ_COMPARISON_THRESHOLD ) return false;
        
        if(other.im == null && im != null 
                || im == null && other.im != null 
                || im.length != other.im.length) return false;
        for (int i = 0; i < im.length; i++)
            if( Math.abs(other.im[i]-im[i]) > EQ_COMPARISON_THRESHOLD ) return false;
        return true;
    }
    
    
    
    
    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Iterator<Double> iterator() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> T[] toArray(T[] ts) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean add(Double e) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean containsAll(Collection<?> clctn) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean addAll(Collection<? extends Double> clctn) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean addAll(int i, Collection<? extends Double> clctn) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean removeAll(Collection<?> clctn) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean retainAll(Collection<?> clctn) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Double set(int i, Double e) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void add(int i, Double e) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Double remove(int i) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int indexOf(Object o) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ListIterator<Double> listIterator() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ListIterator<Double> listIterator(int i) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<Double> subList(int i, int i1) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    

}
