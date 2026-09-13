class Item
    @constructor
    fn create() -> void
        return
    end
end
@init
fn launch() -> int
    let first: Item = Item()
    let second: Item = first
    return 0
end
