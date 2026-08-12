inject namespace std.file as file
inject namespace std.console as console

@init
fn launch() -> int
    let contents: string = file::read_text("input.txt")
    let empty: string = console::read_line()
    let line: string = console::read_line()
    let unterminated: string = console::read_line()

    if contents == "First line\nSegunda ñ\n🐉\n" && empty == "" && line == "Sol 🐉" && unterminated == "final\r" then
        return 42
    end

    return 1
end
