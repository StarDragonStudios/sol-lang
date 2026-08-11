inject namespace std.string as strings

@init
fn launch() -> int
    let invalid: string = strings::slice("Sol", 2, 1)
    return 0
end
