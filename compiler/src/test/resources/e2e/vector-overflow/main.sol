inject std.collections.vector

@init
fn launch() -> int
    let vector: pointer<Vector<int>> = create_vector<int>()
    vector->length = 4611686018427387904
    vector->capacity = 4611686018427387904
    vector_push<int>(vector, 1)
    return 1
end
