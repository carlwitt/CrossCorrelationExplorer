package Data;

/**
 * Wrapper class for a value that can be reused as long as it is not invalidated.
 * Created by Carl Witt on 27.10.14.
 */
public abstract class Cacheable<T> {

    /** The value to reuse. */
    protected T cachedValue;

    /** Recomputes the value if it is not valid.
     * @return the new cached value */
    public T get(){
        if(!isValid()) recompute();
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

}
