@abstract
class Item
    @constructor
    fn create() -> void
        return
    end
end
fn invalid() -> void
    let item: pointer<Item> = new Item()
    return
end
@init
fn launch() -> int
    return 0
end
