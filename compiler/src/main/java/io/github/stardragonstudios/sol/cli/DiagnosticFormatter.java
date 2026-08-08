package io.github.stardragonstudios.sol.cli;

import io.github.stardragonstudios.sol.source.SourceSpan;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Objects;

public final class DiagnosticFormatter {
    private DiagnosticFormatter() {}

    public static String format(SourceDiagnostic sourceDiagnostic) {
        Objects.requireNonNull(sourceDiagnostic, "Formatted source diagnostic must not be null.");

        var header = formatHeader(sourceDiagnostic);

        try {
            var source = Files.readString(sourceDiagnostic.sourceFile());

            return format(sourceDiagnostic, source);
        } catch (IOException exception) {
            /*
             * Rendering source context is best-effort. A diagnostic must
             * never disappear merely because its source can no longer be
             * read after compilation.
             */
            return header;
        }
    }

    static String format(SourceDiagnostic sourceDiagnostic, String source) {
        Objects.requireNonNull(sourceDiagnostic, "Formatted source diagnostic must not be null.");
        Objects.requireNonNull(source, "Diagnostic source text must not be null.");

        var header = formatHeader(sourceDiagnostic);
        var span = sourceDiagnostic.diagnostic().span();
        var excerpt = sourceExcerpt(source, span);

        if (excerpt == null) return header;

        return header + "\n" + excerpt;
    }

    private static String formatHeader(SourceDiagnostic sourceDiagnostic) {
        var diagnostic = sourceDiagnostic.diagnostic();
        var position = diagnostic.span().start();
        var severity = diagnostic.severity().name().toLowerCase(Locale.ROOT);

        return "%s:%d:%d: %s [%s]: %s".formatted(sourceDiagnostic.sourceFile(), position.line(), position.column(), severity, diagnostic.code(), diagnostic.message());
    }

    private static String sourceExcerpt(String source, SourceSpan span) {
        if (span.start().offset() > source.length() || span.end().offset() > source.length()) return null;

        var lines = source.split("\\r\\n|\\r|\\n", -1);
        var startLine = span.start().line();
        var endLine = span.end().line();

        if (startLine < 1 || endLine < startLine || endLine > lines.length) return null;

        var gutterWidth = Integer.toString(endLine).length();
        var rendered = new StringBuilder();

        rendered.repeat(" ", gutterWidth);
        rendered.append(" |");

        var renderedLine = false;

        for (var lineNumber = startLine; lineNumber <= endLine; lineNumber++) {
            var line = lines[lineNumber - 1];
            var markerStart = lineNumber == startLine ? span.start().column() - 1 : 0;
            var markerEnd = lineNumber == endLine ? span.end().column() - 1 : line.length();

            if (markerStart < 0 || markerStart > line.length() || markerEnd < 0 || markerEnd > line.length()) return null;

            /*
             * SourceSpan is half-open. When a multiline span ends at
             * column 1 of the final line, no character from that line
             * belongs to the span.
             */
            if (lineNumber == endLine && startLine < endLine && markerEnd == 0) continue;
            if (lineNumber == startLine && lineNumber == endLine && markerEnd < markerStart) return null;

            var markerWidth = Math.max(1, markerEnd - markerStart);

            rendered.append("\n");
            rendered.append(("%" + gutterWidth + "d | %s").formatted(lineNumber, line));
            rendered.append("\n");
            rendered.repeat(" ", gutterWidth);
            rendered.append(" | ");
            rendered.append(markerPrefix(line, markerStart));
            rendered.repeat("^", markerWidth);

            renderedLine = true;
        }

        return renderedLine ? rendered.toString() : null;
    }

    private static String markerPrefix(String line, int markerOffset) {
        var prefix = new StringBuilder(markerOffset);

        for (var index = 0; index < markerOffset; index++) prefix.append(line.charAt(index) == '\t' ? '\t' : ' ');

        return prefix.toString();
    }

}
