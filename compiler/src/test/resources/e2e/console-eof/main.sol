inject namespace std.console as console

@init
fn launch() -> int
    let line: string = console::read_line()
    return 1
end
