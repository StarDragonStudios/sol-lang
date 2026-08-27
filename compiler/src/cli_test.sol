inject std.collections.vector
inject cli.core

@init
fn launch() -> int
    let unix: pointer<Vector<string>> = compiler_request_fields("SOL-SELFHOST-REQUEST-1\nsource.sol\nroot\nsource\nstdlib\nmodule.ll\nliterals.c\n")
    if unix == null || vector_length<string>(unix) != 7 || vector_get<string>(unix, 1) != "source.sol" then
        destroy_vector<string>(unix)
        return 1
    end
    destroy_vector<string>(unix)

    let windows: pointer<Vector<string>> = compiler_request_fields("SOL-SELFHOST-REQUEST-1\r\nsource.sol\r\nroot\r\nsource\r\nstdlib\r\nmodule.ll\r\nliterals.c\r\n")
    if windows == null || vector_get<string>(windows, 6) != "literals.c" then
        destroy_vector<string>(windows)
        return 2
    end
    destroy_vector<string>(windows)

    if compiler_request_fields("SOL-SELFHOST-REQUEST-1\nmissing\n") != null then
        return 3
    end
    if compiler_request_fields("WRONG\na\nb\nc\nd\ne\nf\n") != null then
        return 4
    end
    if compiler_module_relative_path("utilities.math") != "utilities/math.sol" then
        return 5
    end
    if compiler_standard_path("stdlib", "std.collections.vector") != "stdlib/std/collections/vector.sol" then
        return 6
    end
    if compiler_standard_path("stdlib", "user.module") != "" then
        return 7
    end
    return 0
end
