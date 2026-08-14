inject frontend.source only SourcePosition, SourceSpan, source_span

struct LexicalDiagnostic
    code: string
    message: string
    span: SourceSpan
end

fn lexical_diagnostic(code: string, message: string, start: SourcePosition, end_position: SourcePosition) -> LexicalDiagnostic
    return LexicalDiagnostic {
        code: code,
        message: message,
        span: source_span(start, end_position)
    }
end

fn empty_lexical_diagnostic() -> LexicalDiagnostic
    let position: SourcePosition = SourcePosition {
        offset: 0,
        line: 1,
        column: 1
    }

    return LexicalDiagnostic {
        code: "",
        message: "",
        span: source_span(position, position)
    }
end
