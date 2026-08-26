inject namespace std.console as console

@init
fn launch() -> int
    let input: string = console::read_line()
    console::print_line(input)
    if input == "input" then
        return 29
    end
    return 1
end
