inject namespace std.console as console
inject namespace std.file as files
inject namespace std.memory as memory
inject namespace std.string as strings
inject std.collections.vector

struct Point
    x: int
    y: int
end

struct Pair<T>
    first: T
    second: T
end

fn identity<T>(value: T) -> T
    return value
end

fn calculate() -> int
    const limit: int = 5
    @mut let index: int = 0
    @mut let total: int = 0
    while index < limit do
        if index % 2 == 0 then
            total = total + index
        else
            total = total + 1
        end
        index = index + 1
    end
    return total
end

@init
fn launch() -> int
    @mut let point: Point = Point { x: 20, y: 21 }
    point.x = point.x + 1
    let copied: Point = identity<Point>(point)
    let pair: Pair<int> = Pair<int> { first: copied.x, second: copied.y }

    let pointer: pointer<Point> = memory::allocate<Point>(1)
    if pointer == null then
        return 1
    end
    pointer->x = pair.first
    pointer->y = pair.second
    let pointer_total: int = pointer->x + pointer->y
    memory::free<Point>(pointer)

    @mut let numbers: pointer<int> = memory::allocate<int>(1)
    memory::store<int>(numbers, 40)
    numbers = memory::reallocate<int>(numbers, 2)
    if numbers == null then
        return 2
    end
    memory::store_at<int>(numbers, 1, 2)
    let memory_total: int = memory::load<int>(numbers) + memory::load_at<int>(numbers, 1)
    memory::free<int>(numbers)

    let values: pointer<Vector<int>> = create_vector<int>()
    vector_push<int>(values, identity<int>(40))
    vector_push<int>(values, 2)
    vector_set<int>(values, 1, vector_get<int>(values, 1))
    let vector_total: int = vector_pop<int>(values) + vector_get<int>(values, 0)
    vector_clear<int>(values)
    let vector_valid: boolean = vector_length<int>(values) == 0 && vector_capacity<int>(values) >= 2
    destroy_vector<int>(values)

    let text: string = "conformance " + "🐉"
    let text_valid: boolean = strings::length(text) == 13 && text[12] == '🐉' && strings::slice(text, 0, 11) == "conformance" && strings::substring(text, 12, 1) == "🐉" && text != "other"
    let arithmetic_valid: boolean = calculate() == 8 && -1 + 2 * 3 == 5 && 7 / 2 == 3 && 7 % 2 == 1
    let float_valid: boolean = 1.5 + 2.5 == 4.0 && 4.0 / 2.0 >= 2.0
    let logical_valid: boolean = !false && true || false
    let path: string = "conformance-output.txt"
    let file_valid: boolean = files::write_text(path, "file") && files::append_text(path, " 🐉") && files::exists(path) && files::read_text(path) == "file 🐉"
    console::print_line(text)
    let invalid_memory: boolean = memory::allocate<int>(0) == null && memory::reallocate<int>(null, -1) == null
    if pointer_total == 42 && memory_total == 42 && vector_total == 42 && vector_valid && text_valid && arithmetic_valid && float_valid && logical_valid && file_valid && invalid_memory then
        return 41
    end
    return 3
end
