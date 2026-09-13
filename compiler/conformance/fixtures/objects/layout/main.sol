struct LayoutPayload
    flag: boolean
    amount: float
    marker: char
    total: int
end
@interface
class LayoutRole
end
class LayoutDerived << LayoutBase < LayoutRole
    payload: LayoutPayload
    child: LayoutChild
    label: string
    @constructor
    fn build(value: int) -> void
        base(value)
        this.payload = LayoutPayload { flag: true, amount: 1.5, marker: 'λ', total: value }
        this.child = LayoutChild(value + 1)
        this.label = "Sol 🐉"
        this.payload.total = this.private_total()
    end
    @private
    fn private_total() -> int
        return this.base_value + this.child.value
    end
end
class LayoutBase
    base_value: int
    @constructor
    fn build(value: int) -> void
        this.base_value = value
    end
end
class LayoutChild
    value: int
    @constructor
    fn build(value: int) -> void
        this.value = value
    end
end
class LayoutEmpty
    @constructor
    fn build() -> void
        return
    end
end
fn object_layout_check() -> int
    @mut let item: LayoutDerived = LayoutDerived(10)
    if item.base_value != 10 || item.child.value != 11 || item.payload.total != 21 || !item.payload.flag || item.payload.amount != 1.5 || item.payload.marker != 'λ' || item.label != "Sol 🐉" then
        return 1
    end
    item = (LayoutDerived(20))
    item.child = (LayoutChild(9))
    item.payload.total = 42
    if item.base_value != 20 || item.child.value != 9 || item.payload.total != 42 then
        return 2
    end
    let heap: pointer<LayoutDerived> = new LayoutDerived(30)
    if heap == null then
        return 3
    end
    let base_view: pointer<LayoutBase> = heap
    base_view->base_value = 31
    let role: pointer<LayoutRole> = heap
    let alias: pointer<LayoutRole> = heap
    heap->child.value = 32
    let valid: boolean = heap->base_value == 31 && heap->child.value == 32 && role == alias
    delete heap
    if !valid then
        return 4
    end
    let first: pointer<LayoutEmpty> = new LayoutEmpty()
    let second: pointer<LayoutEmpty> = new LayoutEmpty()
    let distinct: boolean = first != null && second != null && first != second
    delete first
    delete second
    let empty: pointer<LayoutEmpty> = null
    delete empty
    let absent: pointer<LayoutDerived> = null
    let absent_base: pointer<LayoutBase> = absent
    if !distinct || absent_base != null then
        return 5
    end
    return 0
end
@init
fn launch() -> int
    return object_layout_check()
end
