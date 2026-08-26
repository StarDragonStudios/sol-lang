inject std.collections.vector

@init
fn launch() -> int
    let values: pointer<Vector<int>> = create_vector<int>()
    vector_pop<int>(values)
    return 0
end
