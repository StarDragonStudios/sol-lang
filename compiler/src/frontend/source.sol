struct SourcePosition
    offset: int
    line: int
    column: int
end

struct SourceSpan
    start: SourcePosition
    end_position: SourcePosition
end

fn source_position(offset: int, line: int, column: int) -> SourcePosition
    return SourcePosition {
        offset: offset,
        line: line,
        column: column
    }
end

fn source_span(start: SourcePosition, end_position: SourcePosition) -> SourceSpan
    return SourceSpan {
        start: start,
        end_position: end_position
    }
end

fn source_span_length(span: SourceSpan) -> int
    return span.end_position.offset - span.start.offset
end
