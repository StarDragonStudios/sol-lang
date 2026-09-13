class Base
    fn value() -> int
        return 1
    end
end
class Child << Base
    fn value() -> int
        return 2
    end
end
@init
fn launch() -> int
    return 0
end
