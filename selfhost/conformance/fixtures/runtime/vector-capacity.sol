inject std.collections.vector

@init
fn launch() -> int
    let values: pointer<Vector<int>> = create_vector<int>()
    vector_reserve<int>(values, -1)
    return 0
end
