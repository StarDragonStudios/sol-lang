inject namespace std.file as files

@init
fn launch() -> int
    files::read_text("missing-file.txt")
    return 0
end
