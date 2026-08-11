inject helper only Pair, Box, pair, box, identity

@init
fn launch() -> int
    let nested: Box<Pair<int, int>> = box<Pair<int, int>>(pair<int, int>(19, 23))
    let enabled: boolean = identity<boolean>(true)
    let value: int = identity<int>(nested.value.first + nested.value.second)

    if enabled then
        return value
    else
        return 0
    end
end
