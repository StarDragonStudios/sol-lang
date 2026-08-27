inject helper only answer
inject namespace std.console as console
inject std.collections.vector

fn marker() -> int
    return 42
end

@init
fn launch() -> int
    let values: pointer<Vector<int>> = create_vector<int>()
    vector_push<int>(values, 40)
    vector_push<int>(values, 2)
    let valid: boolean = vector_get<int>(values, 0) + vector_get<int>(values, 1) == answer()
    destroy_vector<int>(values)
    console::print_line("self-host CLI 🐉")
    if valid then
        return 37
    end
    return 1
end
