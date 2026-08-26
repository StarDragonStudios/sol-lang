inject std.collections.vector

@init
fn launch() -> int
    let values: pointer<Vector<int>> = create_vector<int>()
    vector_get<int>(values, 0)
    return 0
end
