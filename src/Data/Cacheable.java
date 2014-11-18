package Data;

/**
 * Wrapper class for a value that can be reused as long as it is not invalidated.
 * Created by Carl Witt on 27.10.14.
 */
public abstract class Cacheable<T> {

    /** The value to reuse. */
    protected T cachedValue;
    protected boolean invalidated = false;

    /** Recomputes the value if it is not valid.
     * @return the new cached value */
    public T get(){
        if(invalidated){
            recompute();
            invalidated = false;
            return cachedValue;
        }
        if(!isValid()){
            recompute();
        }
        return cachedValue;
    }

    /** @param newValue a value to set the cached value to */
    public void set(T newValue){ cachedValue = newValue; }

    /**
     * Similar to a dirty-bit in a cache.
     * @return whether the cached value is currently valid
     */
    public abstract boolean isValid();

    /** Makes the cached value valid. */
    public abstract void recompute();

    /** Guarantees that the next call to get() will recompute the result. */
    public void invalidate() {
        invalidated = true;
    }
}
