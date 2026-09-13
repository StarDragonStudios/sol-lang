class Base
end
class Child << Base
end
fn invalid(value: pointer<Base>) -> void
    let child: pointer<Child> = value
    return
end
@init
fn launch() -> int
    return 0
end
