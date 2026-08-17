inject helper only answer
inject namespace std.console as console

fn marker() -> int
    return 42
end

@init
fn launch() -> int
    console::print_line("module cycle")
    if answer() == 42 then
        return 37
    end
    return 1
end
