@interface
class View
end
fn invalid(value: pointer<View>) -> void
    delete value
    return
end
@init
fn launch() -> int
    return 0
end
