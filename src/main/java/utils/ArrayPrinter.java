package utils;


public class ArrayPrinter {

    public static String toCompactString(Object[] array) {
        if (array == null)
            return "null";

        int iMax = array.length - 1;
        if (iMax == -1)
            return "[]";

        StringBuilder b = new StringBuilder();
        b.append('[');
        int nullSeries = 0;

        for (int i = 0; ; i++) {
            if (array[i] != null) {
                b.append(array[i]);
            } else {
                nullSeries++;
                if (i == iMax || array[i + 1] != null) {
                    if (nullSeries == 1) {
                        b.append("null");
                    } else {
                        b.append("null*").append(nullSeries);
                    }
                    nullSeries = 0;
                } else {
                    continue;
                }

            }

            if (i == iMax)
                return b.append(']').toString();
            b.append("; ");
        }
    }
}
