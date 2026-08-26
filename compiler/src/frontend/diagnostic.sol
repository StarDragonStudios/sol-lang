inject frontend.source only SourcePosition, SourceSpan, source_span

struct Diagnostic
    code: string
    message: string
    span: SourceSpan
end

fn diagnostic(code: string, message: string, start: SourcePosition, end_position: SourcePosition) -> Diagnostic
    return Diagnostic {
        code: code,
        message: message,
        span: source_span(start, end_position)
    }
end

fn empty_diagnostic() -> Diagnostic
    let position: SourcePosition = SourcePosition {
        offset: 0,
        line: 1,
        column: 1
    }

    return Diagnostic {
        code: "",
        message: "",
        span: source_span(position, position)
    }
end
