inject namespace std.memory as memory

struct Vector<T>
    data: pointer<T>
    length: int
    capacity: int
end

@fn _vector_fail_allocation() -> void
@fn _vector_fail_bounds() -> void
@fn _vector_fail_capacity() -> void
@fn _vector_fail_empty_pop() -> void

fn create_vector<T>() -> pointer<Vector<T>>
    let vector: pointer<Vector<T>> = memory::allocate<Vector<T>>(1)
    if vector == null then
        _vector_fail_allocation()
    end
    vector->data = null
    vector->length = 0
    vector->capacity = 0
    return vector
end

fn destroy_vector<T>(vector: pointer<Vector<T>>) -> void
    if vector == null then
        return
    end
    memory::free<T>(vector->data)
    vector->data = null
    vector->length = 0
    vector->capacity = 0
    memory::free<Vector<T>>(vector)
    return
end

fn vector_length<T>(vector: pointer<Vector<T>>) -> int
    return vector->length
end

fn vector_capacity<T>(vector: pointer<Vector<T>>) -> int
    return vector->capacity
end

fn vector_get<T>(vector: pointer<Vector<T>>, index: int) -> T
    if index < 0 || index >= vector->length then
        _vector_fail_bounds()
    end
    return memory::load_at<T>(vector->data, index)
end

fn vector_set<T>(vector: pointer<Vector<T>>, index: int, value: T) -> void
    if index < 0 || index >= vector->length then
        _vector_fail_bounds()
    end
    memory::store_at<T>(vector->data, index, value)
    return
end

fn vector_reserve<T>(vector: pointer<Vector<T>>, requested: int) -> void
    if requested < 0 then
        _vector_fail_capacity()
    end
    if requested <= vector->capacity then
        return
    end
    let resized: pointer<T> = memory::reallocate<T>(vector->data, requested)
    if resized == null then
        _vector_fail_allocation()
    end
    vector->data = resized
    vector->capacity = requested
    return
end

fn vector_push<T>(vector: pointer<Vector<T>>, value: T) -> void
    if vector->length == vector->capacity then
        @mut let next_capacity: int = 8
        if vector->capacity > 0 then
            if vector->capacity > 4611686018427387903 then
                _vector_fail_capacity()
            end
            next_capacity = vector->capacity * 2
        end
        vector_reserve<T>(vector, next_capacity)
    end
    memory::store_at<T>(vector->data, vector->length, value)
    vector->length = vector->length + 1
    return
end

fn vector_pop<T>(vector: pointer<Vector<T>>) -> T
    if vector->length == 0 then
        _vector_fail_empty_pop()
    end
    let index: int = vector->length - 1
    let value: T = memory::load_at<T>(vector->data, index)
    vector->length = index
    return value
end

fn vector_clear<T>(vector: pointer<Vector<T>>) -> void
    vector->length = 0
    return
end
