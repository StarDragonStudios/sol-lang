package io.github.stardragonstudios.sol.backend.c;

import java.util.ArrayList;
import java.util.Objects;

final class CSourceWriter {
    private static final String INDENTATION = "    ";

    private final ArrayList<String> lines = new ArrayList<>();

    private int indentationLevel;

    void line(String text) {
        Objects.requireNonNull(text, "C source line must not be null.");

        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0)
            throw new IllegalArgumentException("A C source line must not contain line breaks.");

        if (text.indexOf('\t') >= 0)
            throw new IllegalArgumentException("A C source line must not contain tab characters.");

        if (!text.equals(text.stripTrailing()))
            throw new IllegalArgumentException("A C source line must not contain trailing whitespace.");

        if (text.isEmpty()) {
            blankLine();
            return;
        }

        lines.add(INDENTATION.repeat(indentationLevel) + text);
    }

    void blankLine() {
        if (lines.isEmpty() || lines.getLast().isEmpty()) return;

        lines.add("");
    }

    void indent() {
        indentationLevel++;
    }

    void dedent() {
        if (indentationLevel == 0) {
            throw new IllegalStateException("C source indentation cannot be decreased below zero.");
        }

        indentationLevel--;
    }

    void openBlock(String header) {
        Objects.requireNonNull(header, "C block header must not be null.");

        if (header.isBlank()) throw new IllegalArgumentException("C block header must not be blank.");

        line(header + " {");
        indent();
    }

    void closeBlock() {
        dedent();
        line("}");
    }

    String source() {
        var lastContentIndex = lines.size();

        while (lastContentIndex > 0 && lines.get(lastContentIndex - 1).isEmpty()) lastContentIndex--;

        if (lastContentIndex == 0) return "";

        return String.join("\n", lines.subList(0, lastContentIndex)) + "\n";
    }
}
