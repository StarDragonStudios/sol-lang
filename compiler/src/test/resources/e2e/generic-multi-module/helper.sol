struct Pair<A, B>
    first: A
    second: B
end

struct Box<T>
    value: T
end

fn pair<A, B>(first: A, second: B) -> Pair<A, B>
    return Pair<A, B> { first: first, second: second }
end

fn box<T>(value: T) -> Box<T>
    return Box<T> { value: value }
end

fn identity<T>(value: T) -> T
    return value
end
