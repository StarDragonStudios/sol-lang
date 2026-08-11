inject namespace std.string as strings

@init
fn launch() -> int
    let text: string = "Aé🐉Z"

    if strings::length(text) != 4 then
        return 1
    end

    if strings::length("") != 0 then
        return 2
    end

    if text[0] != 'A' || text[1] != 'é' || text[2] != '🐉' || text[3] != 'Z' then
        return 3
    end

    let middle: string = strings::slice(text, 1, 3)
    let same_middle: string = strings::substring(text, 1, 2)

    if middle != "é🐉" || same_middle != middle then
        return 4
    end

    if strings::slice(text, 0, 0) != "" || strings::slice(text, 4, 4) != "" then
        return 5
    end

    let combined: string = "Sol " + "🐉" + " UTF-8"

    if combined != "Sol 🐉 UTF-8" || "" + "Sol" != "Sol" || "Sol" + "" != "Sol" then
        return 6
    end

    if "é" == "é" then
        return 7
    end

    return 42
end
