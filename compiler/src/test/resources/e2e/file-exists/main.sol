inject namespace  std.file as file

@init
fn launch() -> int
    if file::exists("present.txt") then
        if file::exists("missing.txt") then
            return 12
        else
            return 23
        end
    else
        return 11
    end
end
