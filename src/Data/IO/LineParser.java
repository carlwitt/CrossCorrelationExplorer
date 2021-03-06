package Data.IO;

import com.google.common.base.Splitter;

import java.util.ArrayList;
import java.util.StringTokenizer;

/** Defines how the lines of the text file are to be separated into strings that are then parsed into numbers.
 * Provides a method to perform the parsing.
 */
public class LineParser {

    public enum SplitMethod {
        BY_CHARACTER, BY_COLUMN_WIDTH
    }
    
    private final LineParser.SplitMethod splitMethod;
    
    private final int columnWidth;
    private final String separator;
    private final Splitter stringSplitter;
    
    public LineParser(int columnWidth) {
        this.splitMethod = LineParser.SplitMethod.BY_COLUMN_WIDTH;
        this.columnWidth = columnWidth;
        this.separator = null;
        stringSplitter = Splitter.fixedLength(columnWidth);
    }
    public LineParser(String separator) {
        this.splitMethod = LineParser.SplitMethod.BY_CHARACTER;
        this.separator = separator;
        this.columnWidth = 0;
        stringSplitter = Splitter.on(separator);
    }

    /**
     * Splits a string according to a delimiter string.
     * @param input The string to split
     * @return Array of strings which give the original string if glued with the delimiter string.
     */
    private String[] splitString(String delimiter, String input) {
        StringTokenizer st = new StringTokenizer(input, delimiter);
        String[] parts = new String[st.countTokens()];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = st.nextToken();
        }
        return parts;
    }

    // splits a string (a line) into a sequence of double values
    public double[] splitToDouble(String line) throws NumberFormatException {
        Iterable<String> pieces = stringSplitter.trimResults().omitEmptyStrings().split(line); // omitEmptyStrings() would leave gaps in the time series where there should be NaN
        ArrayList<Double> result = new ArrayList<>();
        for (String trimmedNumber : pieces) {
            double parsed = Double.parseDouble(trimmedNumber);
            result.add(parsed);
        }
        // convert array list to primitive array (.toArray works only for Double)
        double[] result2 = new double[result.size()];
        for (int i = 0; i < result.size(); i++) {
            result2[i] = result.get(i);
        }
        return result2;
    }

}
