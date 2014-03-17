package Data.Correlation;

import Data.TimeSeries;
import Global.RuntimeConfiguration;
import com.google.common.base.Joiner;
import java.util.Collection;
import java.util.HashMap;

/**
 *
 * @author Carl Witt
 */
public class CorrelogramStore {

    static HashMap<CorrelogramMetadata, CorrelationMatrix> correlationMatricesByMetadata = new HashMap<CorrelogramMetadata, CorrelationMatrix>();
    static HashMap<Integer, CorrelationMatrix> correlogramsById = new HashMap<Integer, CorrelationMatrix>();
    
    public static int getSize() {
        return correlogramsById.size();
    }

    public static boolean contains(int id) {
        return correlogramsById.containsKey(id);
    }

    public static boolean contains(CorrelogramMetadata metadata) {
        return correlationMatricesByMetadata.containsKey(metadata);
    }
    
    public static void append(CorrelationMatrix c) {
        correlogramsById.put(c.id, c);
        correlationMatricesByMetadata.put(c.metadata, c);
    }

    public static Collection<CorrelationMatrix> getAllResults() {
        System.out.println(String.format("Ids:\n%s\nMetadata:\n%s", correlogramsById.keySet(), correlationMatricesByMetadata.keySet()));
        return correlogramsById.values();
    }

    public static CorrelationMatrix getResult(int id) {
        return correlogramsById.get(id);
    }

    /** The main access point for retrieving results. Computes the result if not already present in the cache. */
    public static CorrelationMatrix getResult(CorrelogramMetadata metadata) {
        
        CorrelationMatrix result = correlationMatricesByMetadata.get(metadata);

        // compute result if not yet computed
        if(result == null){
            
            if(metadata.setA.size() == 1 && metadata.setB.size() == 1){
                TimeSeries ts1 = metadata.setA.get(0);
                TimeSeries ts2 = metadata.setB.get(0);
                result = DFT.crossCorrelation(ts1, ts2, metadata.windowSize);
            } else{
                result = new CorrelationMatrix(metadata);
            }
            
//            System.out.println("correlation result: "+result);
            
            append(result); // id and metadata are contained in the result
//            System.out.println("Result computed. "+metadata);
//            System.out.println("Keyset\n"+Joiner.on("\n").join(correlationMatricesByMetadata.keySet()));
        } else {
            if(RuntimeConfiguration.VERBOSE) System.out.println("Cache hit.");
        }
        
        return result;
    }

}
