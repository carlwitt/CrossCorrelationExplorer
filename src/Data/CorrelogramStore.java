package Data;

import Data.Correlation.CorrelationMatrix;
import Data.Windowing.WindowMetadata;
import Global.RuntimeConfiguration;

import java.util.Collection;
import java.util.HashMap;

/**
 * TODO: make the correlogram store non global/static because that's better for exchanging data sets.
 * @author Carl Witt
 */
public class CorrelogramStore {

    private static final HashMap<WindowMetadata, CorrelationMatrix> correlationMatricesByMetadata = new HashMap<>();
    private static final HashMap<Integer, CorrelationMatrix> correlogramsById = new HashMap<>();
    
    public static int getSize() {
        return correlogramsById.size();
    }

    public static boolean contains(int id) {
        return correlogramsById.containsKey(id);
    }
    public static boolean contains(WindowMetadata metadata) {
        return correlationMatricesByMetadata.containsKey(metadata);
    }
    
    public static void append(CorrelationMatrix c) {
        correlationMatricesByMetadata.put(c.metadata, c);
    }

    public static Collection<CorrelationMatrix> getAllResults() {
//        System.out.println(String.format("Ids:\n%s\nMetadata:\n%s", correlogramsById.keySet(), correlationMatricesByMetadata.keySet()));
        return correlationMatricesByMetadata.values();
    }

    /** The main access point for retrieving results. 
     * @param metadata the input for the correlation computation
     * @return the cached or computed result (if not present in the cache)
     */
    public static CorrelationMatrix getResult(WindowMetadata metadata) {

        CorrelationMatrix result = correlationMatricesByMetadata.get(metadata);

        // compute result if not yet computed
        if(result == null || ! result.metadata.equals(metadata)){
            
            // 1-against-1 correlations are done directly
//            if(metadata.setA.size() == 1 && metadata.setB.size() == 1){
//                result = CrossCorrelation.naiveCrossCorrelation(metadata);
//            } else{ // complex requests require aggregation which takes place in the correlation matrix (which tries to reuse 1-against-1 correlations from the correlogram store)
                result = new CorrelationMatrix(metadata);
                result.compute();
//            }
            
            append(result); // id and metadata are contained in the result
            
//System.out.println("Result computed. "+metadata);
//System.out.println(String.format("result appended %s", result));
//System.out.println("Keyset\n"+Joiner.on("\n").join(correlationMatricesByMetadata.keySet()));
            
        } else {
            if(RuntimeConfiguration.VERBOSE) System.out.println("Cache hit.");
        }
        
        return result;
    }

    public static void clear() {
        correlationMatricesByMetadata.clear();
        correlogramsById.clear();
    }

}
