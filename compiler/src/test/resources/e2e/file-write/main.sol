inject namespace std.file as file

@init
fn launch() -> int
    if file::write_text("output.txt", "Hola ñ") then
        if file::append_text("output.txt", " Sol") then
            return 23
        else
            return 12
        end
    else
        return 11
    end
end
