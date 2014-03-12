package Data;

import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;

/** Defines how the lines of the text file are to be separated into strings that are then parsed into numbers.
 * Provides a method to perform the parsing.
 */
public class Separator {

    public enum SplitMethod {

        BY_CHARACTER, BY_COLUMN_WIDTH
    }
    Separator.SplitMethod splitMethod;
    public final int columnWidth;
    public final String separator;
    private final Splitter stringSplitter;

    public Separator(int columnWidth) {
        this.splitMethod = Separator.SplitMethod.BY_COLUMN_WIDTH;
        this.columnWidth = columnWidth;
        this.separator = null;
        stringSplitter = Splitter.fixedLength(columnWidth);
    }

    public Separator(String separator) {
        this.splitMethod = Separator.SplitMethod.BY_CHARACTER;
        this.separator = separator;
        this.columnWidth = 0;
        stringSplitter = Splitter.on(separator);
    }

    /**
     * Splits a string according to a delimiter string.
     * @param line The string to split
     * @return Array of strings which give the original string if glued with the delimiter string.
     */
    protected String[] splitString(String delimiter, String input) {
        //deprecated
        // String.split
        StringTokenizer st = new StringTokenizer(input, delimiter);
        String[] parts = new String[st.countTokens()];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = st.nextToken();
        }
        return parts;
    }

    // splits a string (a line) into a sequence of double values
    protected double[] splitToDouble(String line) {
        Iterable<String> pieces = stringSplitter.trimResults().split(line); // omitEmptyStrings() would leave gaps in the time series where there should be NaN
        ArrayList<Double> result = new ArrayList<>();
        for (Iterator<String> it = pieces.iterator(); it.hasNext();) {
            String trimmedNumber = it.next();
            try {
                result.add(Double.parseDouble(trimmedNumber));
            } catch (NumberFormatException e) {
                System.out.println(String.format("Couldn't parse double from '%s'\nin line %s", trimmedNumber, line));
            }
        }
        // convert array list to primitive array (.toArray works only for Double)
        double[] result2 = new double[result.size()];
        for (int i = 0; i < result.size(); result2[i] = result.get(i++)) {
            ;
        }
        return result2;
    }

}
