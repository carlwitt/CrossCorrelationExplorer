package Data.Correlation;

import Data.DataModel;
import Data.TimeSeries;
import com.google.common.base.Joiner;
import java.util.Collection;
import java.util.HashMap;

/**
 *
 * @author Carl Witt
 */
public class CorrelogramStore {

    final DataModel dataModel;
    
    static HashMap<CorrelogramMetadata, CorrelationMatrix> correlogramsByMetadata = new HashMap<CorrelogramMetadata, CorrelationMatrix>();
    static HashMap<Integer, CorrelationMatrix> correlogramsById = new HashMap<Integer, CorrelationMatrix>();
    
    public CorrelogramStore(DataModel d){
        this.dataModel = d;
    }
    
    public static int getSize() {
        return correlogramsById.size();
    }

    public static boolean contains(int id) {
        return correlogramsById.containsKey(id);
    }

    public static boolean contains(CorrelogramMetadata metadata) {
        return correlogramsByMetadata.containsKey(metadata);
    }
    
    public static void append(CorrelationMatrix c) {
        correlogramsById.put(c.id, c);
        correlogramsByMetadata.put(c.metadata, c);
    }

    public static Collection<CorrelationMatrix> getAllResults() {
        System.out.println(String.format("Ids:\n%s\nMetadata:\n%s", correlogramsById.keySet(), correlogramsByMetadata.keySet()));
        return correlogramsById.values();
    }

    public static CorrelationMatrix getResult(int id) {
        return correlogramsById.get(id);
    }

    /** The main access point for retrieving results. Computes the result if necessary. */
    public static CorrelationMatrix getResult(CorrelogramMetadata metadata) {
        
        CorrelationMatrix result = correlogramsByMetadata.get(metadata);

        // compute result if not yet computed
        if(result == null){
            
            if(metadata.timeSeries.size() == 2){
                TimeSeries ts1 = metadata.timeSeries.get(0);
                TimeSeries ts2 = metadata.timeSeries.get(1);
                result = DFT.crossCorrelation(ts1, ts2, metadata.windowSize);
            } else{
                result = new CorrelationMatrix(metadata.timeSeries, metadata.windowSize);
            }
            
//            System.out.println("correlation result: "+result);
            
            append(result); // id and metadata are contained in the result
//            System.out.println("Result computed. "+metadata);
//            System.out.println("Keyset\n"+Joiner.on("\n").join(correlogramsByMetadata.keySet()));
        } else {
//            System.out.println("Cache hit.");
        }
            
        
        return result;
    }

}
