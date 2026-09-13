class Item
    @private
    value: int
end
fn invalid(item: pointer<Item>) -> int
    return item->value
end
@init
fn launch() -> int
    return 0
end
