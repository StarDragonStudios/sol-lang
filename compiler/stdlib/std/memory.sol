@fn allocate<T>(count: int) -> pointer<T>
@fn reallocate<T>(value: pointer<T>, count: int) -> pointer<T>
@fn free<T>(value: pointer<T>) -> void
@fn load<T>(value: pointer<T>) -> T
@fn store<T>(target: pointer<T>, value: T) -> void
@fn load_at<T>(value: pointer<T>, index: int) -> T
@fn store_at<T>(target: pointer<T>, index: int, value: T) -> void
