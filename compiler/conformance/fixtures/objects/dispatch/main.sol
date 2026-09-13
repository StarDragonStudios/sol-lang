@interface
class DispatchLeft
    @fn value() -> int
    @fn echo<T>(value: T) -> T
    @fn change(value: int) -> void
end
@interface
class DispatchRight
    @fn value() -> int
    @fn echo<T>(value: T) -> T
end
@interface
class DispatchJoined < DispatchLeft, DispatchRight
end
@abstract
class DispatchBase < DispatchJoined
    seed: int
    construction_marker: int
    @constructor
    fn build(value: int) -> void
        this.seed = value
        this.construction_marker = 0
        this.construction_marker = this.constructor_marker()
        this.seed = this.value()
    end
    @override
    fn value() -> int
        return this.seed + 1
    end
    @override
    fn echo<T>(value: T) -> T
        return value
    end
    @override
    fn change(value: int) -> void
        this.seed = value
        return
    end
    @fn abstract_value() -> int
    fn constructor_marker() -> int
        return 1
    end
    fn through_this() -> int
        return this.value()
    end
    @private
    fn private_value() -> int
        return this.seed + 5
    end
    fn call_private() -> int
        return this.private_value()
    end
    fn pick(value: int) -> int
        return value + 1
    end
    fn pick(value: string) -> int
        return 5
    end
end
class DispatchDerived << DispatchBase
    extra: int
    @constructor
    fn build() -> void
        base(10)
        this.extra = 100
    end
    @override
    fn value() -> int
        return base.value() + this.extra
    end
    @override
    fn abstract_value() -> int
        return 77
    end
    @override
    fn constructor_marker() -> int
        return 9
    end
    @override
    fn echo<T>(value: T) -> T
        return value
    end
    @override
    fn pick(value: int) -> int
        return value + 10
    end
end
class DispatchGrand << DispatchDerived
    last: int
    @constructor
    fn build() -> void
        base()
        this.last = 1000
    end
    @override
    fn value() -> int
        return base.value() + this.last
    end
end
class DispatchOther << DispatchBase
    @constructor
    fn build() -> void
        base(20)
    end
    @override
    fn value() -> int
        return this.seed + 200
    end
    @override
    fn abstract_value() -> int
        return 88
    end
end
class DispatchHolder
    child: DispatchGrand
    @constructor
    fn build() -> void
        this.child = DispatchGrand()
    end
end
fn object_dispatch_check() -> int
    @mut let direct: DispatchGrand = DispatchGrand()
    if direct.construction_marker != 1 || direct.constructor_marker() != 9 || direct.seed != 11 || direct.value() != 1112 || direct.through_this() != 1112 then
        return 1
    end
    direct = DispatchGrand()
    let holder: DispatchHolder = DispatchHolder()
    if direct.value() != 1112 || holder.child.value() != 1112 then
        return 2
    end
    let concrete: pointer<DispatchGrand> = new DispatchGrand()
    if concrete == null then
        return 3
    end
    let base_view: pointer<DispatchBase> = concrete
    let left: pointer<DispatchLeft> = concrete
    let right: pointer<DispatchRight> = concrete
    let joined: pointer<DispatchJoined> = concrete
    if base_view->value() != 1112 || left->value() != 1112 || right->value() != 1112 || joined->value() != 1112 then
        delete concrete
        return 4
    end
    if left->echo<int>(42) != 42 || right->echo<int>(84) != 84 || joined->echo<string>("Sol 🦊") != "Sol 🦊" then
        delete concrete
        return 5
    end
    left->change(4)
    if right->value() != 1105 || base_view->abstract_value() != 77 || base_view->call_private() != 9 || base_view->pick(5) != 15 || base_view->pick("x") != 5 then
        delete concrete
        return 6
    end
    delete concrete
    let other: pointer<DispatchOther> = new DispatchOther()
    if other == null then
        return 7
    end
    let other_view: pointer<DispatchLeft> = other
    let valid: boolean = other_view->value() == 221 && other_view->echo<int>(9) == 9
    delete other
    if !valid then
        return 8
    end
    return 0
end
@init
fn launch() -> int
    return object_dispatch_check()
end
