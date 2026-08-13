inject std.collections.vector

@init
fn launch() -> int
    let vector: pointer<Vector<int>> = create_vector<int>()
    return vector_pop<int>(vector)
end
