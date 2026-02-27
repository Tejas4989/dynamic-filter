package com.example.filter.util;

/**
 * String utility methods for the filter package.
 */
public final class StringUtils {

    private StringUtils() {}

    /**
     * Converts camelCase to snake_case.
     * Example: "userId" -> "user_id", "dealAmount" -> "deal_amount"
     */
    public static String toSnakeCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
