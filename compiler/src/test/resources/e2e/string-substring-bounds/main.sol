inject namespace std.string as strings

@init
fn launch() -> int
    let invalid: string = strings::substring("Sol", 1, 3)
    return 0
end
