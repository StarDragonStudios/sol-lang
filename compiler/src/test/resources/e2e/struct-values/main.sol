struct Point
    x: int
    y: int
end

struct Box
    point: Point
end

struct Marker
end

fn shifted(point: Point) -> Point
    @mut let result: Point = point
    result.x = result.x + 10
    return result
end

@init
fn launch() -> int
    let marker: Marker = Marker {}
    let original: Point = Point { x: 12, y: 1 }
    let moved: Point = shifted(original)
    @mut let box: Box = Box { point: moved }

    box.point.y = 19

    return moved.x + moved.y + box.point.y
end
