package com.jloom.util;

import java.util.Map;

public final class Tokens {

    private Tokens() {
    }

    public static String substitute(String text, Map<String, String> tokens) {
        String result = text;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
