class Base
end
class Child << Base
end
class Caller
    fn accept(value: pointer<Base>) -> void
        return
    end
end
fn invalid(caller: pointer<Caller>, child: pointer<Child>) -> void
    caller->accept(child)
    return
end
@init
fn launch() -> int
    return 0
end
