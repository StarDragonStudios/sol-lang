inject namespace std.console as csl

@init
fn launch() -> int
    csl::print("Hello ")
    csl::print_line("Sol ñ")
    return 23
end
