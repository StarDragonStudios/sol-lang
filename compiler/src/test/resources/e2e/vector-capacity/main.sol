inject std.collections.vector

@init
fn launch() -> int
    let vector: pointer<Vector<int>> = create_vector<int>()
    vector_reserve<int>(vector, -1)
    return 1
end
