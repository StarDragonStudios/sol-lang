inject std.collections.vector

@init
fn launch() -> int
    let vector: pointer<Vector<int>> = create_vector<int>()
    let value: int = vector_get<int>(vector, 0)
    return value
end
