inject namespace std.file as file
inject namespace std.console as console

@init
fn launch() -> int
    let contents: string = file::read_text("input.txt")
    let empty: string = console::read_line()
    let line: string = console::read_line()
    let unterminated: string = console::read_line()

    if contents != "First line\nSegunda ñ\n🐉\n" then
        return 1
    end

    if empty != "" then
        return 2
    end

    if line != "Sol 🐉" then
        return 3
    end

    if unterminated != "final\r" then
        return 4
    end

    return 42
end
