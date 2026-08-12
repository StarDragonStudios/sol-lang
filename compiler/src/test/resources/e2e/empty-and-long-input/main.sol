inject namespace std.file as file
inject namespace std.console as console
inject namespace std.string as strings

@init
fn launch() -> int
    let contents: string = file::read_text("empty.txt")
    let line: string = console::read_line()

    if contents == "" && strings::length(line) == 600 then
        return 42
    end

    return 1
end
