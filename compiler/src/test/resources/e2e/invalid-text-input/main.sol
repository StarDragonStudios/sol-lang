inject namespace std.file as file

@init
fn launch() -> int
    let contents: string = file::read_text("invalid.txt")
    return 1
end
