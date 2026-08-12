inject namespace std.memory as memory

struct Pair
    first: int
    second: int
end

struct Empty
end

@init
fn launch() -> int
    @mut let values: pointer<Pair> = memory::allocate<Pair>(2)

    if values == null then
        return 1
    end

    values->first = 19
    values->second = 1
    memory::store_at<Pair>(values, 1, Pair { first: 20, second: 2 })

    let grown: pointer<Pair> = memory::reallocate<Pair>(values, 3)

    if grown == null then
        memory::free<Pair>(values)
        return 2
    end

    values = grown
    memory::store_at<Pair>(values, 2, Pair { first: 0, second: 0 })

    let rejected: pointer<Pair> = memory::reallocate<Pair>(values, -1)

    if rejected != null then
        memory::free<Pair>(rejected)
        return 4
    end

    let first: Pair = memory::load<Pair>(values)
    let second: Pair = memory::load_at<Pair>(values, 1)
    let zero: pointer<int> = memory::allocate<int>(0)
    let negative: pointer<int> = memory::allocate<int>(-1)
    let empty: pointer<Empty> = memory::allocate<Empty>(1)

    if zero != null || negative != null || empty != null then
        memory::free<int>(zero)
        memory::free<int>(negative)
        memory::free<Empty>(empty)
        memory::free<Pair>(values)
        return 3
    end

    let from_null: pointer<int> = memory::reallocate<int>(null, 1)

    if from_null == null then
        memory::free<Pair>(values)
        return 5
    end

    memory::store<int>(from_null, 42)

    let released: pointer<int> = memory::reallocate<int>(from_null, 0)

    if released != null then
        memory::free<int>(released)
        memory::free<Pair>(values)
        return 6
    end

    memory::free<int>(zero)
    memory::free<int>(negative)
    memory::free<Empty>(empty)
    memory::free<Pair>(values)

    return first.first + first.second + second.first + second.second
end
