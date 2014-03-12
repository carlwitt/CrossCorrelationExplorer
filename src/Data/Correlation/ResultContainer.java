package Data.Correlation;
import java.util.List;

public interface ResultContainer<T> {
    
	/* return the number of ResultItems in the container */
	int getSize();
	
	/* return whether the ResultItem with the given id is in the container*/
	boolean contains(int id);
	
	/* return whether getSize() is 0*/
	boolean isEmpty();
	
	/* return all the ResultItems in the container*/
	List<T> getResultItems();
	
	T getItembyID(int id);
	
	/* ResultItem append, and min, max update*/
	void append(T ResultItem);
	
}
