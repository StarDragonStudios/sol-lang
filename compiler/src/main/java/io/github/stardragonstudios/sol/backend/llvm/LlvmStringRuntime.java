package io.github.stardragonstudios.sol.backend.llvm;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.*;

/** Shared native implementation of Sol's immutable UTF-8 string operations. */
final class LlvmStringRuntime {
    private static final String BYTE_OFFSET_NAME = "sol.runtime.string.byte_offset";
    private static final String INDEX_NAME = "sol.runtime.string.index";
    private static final String SLICE_NAME = "sol.runtime.string.slice";
    private static final String SUBSTRING_NAME = "sol.runtime.string.substring";
    private static final String CONCAT_NAME = "sol.runtime.string.concat";
    private static final String EQUAL_NAME = "sol.runtime.string.equal";
    private static final String FAIL_NAME = "sol.runtime.string.fail";

    private final LlvmProgramLoweringContext context;

    private HostFunction byteOffset;
    private HostFunction index;
    private HostFunction slice;
    private HostFunction substring;
    private HostFunction concat;
    private HostFunction equal;
    private HostFunction fail;

    private int diagnosticIndex;

    LlvmStringRuntime(LlvmProgramLoweringContext context) {
        this.context = Objects.requireNonNull(context, "LLVM string runtime context must not be null.");
    }

    LLVMValueRef index(LLVMBuilderRef builder, LLVMValueRef string, LLVMValueRef scalarIndex, String name) {
        return call(builder, indexFunction(), name, string, scalarIndex);
    }

    LLVMValueRef slice(LLVMBuilderRef builder, LLVMValueRef string, LLVMValueRef start, LLVMValueRef end, String name) {
        return call(builder, sliceFunction(), name, string, start, end);
    }

    LLVMValueRef substring(LLVMBuilderRef builder, LLVMValueRef string, LLVMValueRef start, LLVMValueRef count, String name) {
        return call(builder, substringFunction(), name, string, start, count);
    }

    LLVMValueRef concat(LLVMBuilderRef builder, LLVMValueRef left, LLVMValueRef right, String name) {
        return call(builder, concatFunction(), name, left, right);
    }

    LLVMValueRef equal(LLVMBuilderRef builder, LLVMValueRef left, LLVMValueRef right, String name) {
        return call(builder, equalFunction(), name, left, right);
    }

    LLVMValueRef scalarLength(LLVMBuilderRef builder, LLVMValueRef string, String name) {
        var length = LLVMBuildExtractValue(builder, string, 2, name);

        requireValue(length, "string scalar length");

        return length;
    }

    private HostFunction byteOffsetFunction() {
        if (byteOffset != null) return byteOffset;

        var llvmContext = llvmContext();
        var stringType = LlvmStringLowerer.type(llvmContext);
        var i64 = LLVMInt64TypeInContext(llvmContext);

        byteOffset = declareInternal(BYTE_OFFSET_NAME, i64, stringType, i64);

        withBuilder(byteOffset, builder -> {
            var function = byteOffset.value();
            var entry = appendBlock(function, "entry");
            var initialize = appendBlock(function, "initialize");
            var loop = appendBlock(function, "loop");
            var advance = appendBlock(function, "advance");
            var done = appendBlock(function, "done");
            var invalid = appendBlock(function, "invalid");

            LLVMPositionBuilderAtEnd(builder, entry);

            var string = parameter(function, 0, BYTE_OFFSET_NAME);
            var target = parameter(function, 1, BYTE_OFFSET_NAME);
            var scalarLength = extract(builder, string, 2, "scalar_length");
            var zero = integer(i64, 0);
            var targetNegative = LLVMBuildICmp(builder, LLVMIntSLT, target, zero, "target_negative");
            var targetPastEnd = LLVMBuildICmp(builder, LLVMIntSGT, target, scalarLength, "target_past_end");
            var targetInvalid = LLVMBuildOr(builder, targetNegative, targetPastEnd, "target_invalid");
            var byteSlot = LLVMBuildAlloca(builder, i64, "byte_offset_slot");
            var scalarSlot = LLVMBuildAlloca(builder, i64, "scalar_index_slot");

            requireValue(LLVMBuildCondBr(builder, targetInvalid, invalid, initialize), "string offset validation branch");

            LLVMPositionBuilderAtEnd(builder, initialize);
            requireValue(LLVMBuildStore(builder, zero, byteSlot), "initial string byte offset");
            requireValue(LLVMBuildStore(builder, zero, scalarSlot), "initial string scalar index");
            requireValue(LLVMBuildBr(builder, loop), "string offset loop entry");

            LLVMPositionBuilderAtEnd(builder, loop);

            var scalarIndex = LLVMBuildLoad2(builder, i64, scalarSlot, "scalar_index");
            var needsAdvance = LLVMBuildICmp(builder, LLVMIntSLT, scalarIndex, target, "needs_advance");

            requireValue(LLVMBuildCondBr(builder, needsAdvance, advance, done), "string offset loop branch");

            LLVMPositionBuilderAtEnd(builder, advance);

            var data = extract(builder, string, 0, "data");
            var byteIndex = LLVMBuildLoad2(builder, i64, byteSlot, "byte_index");
            var firstByte = loadByte(builder, data, byteIndex, i64, "first_byte");
            var width = utf8Width(builder, firstByte, i64);
            var nextByte = LLVMBuildAdd(builder, byteIndex, width, "next_byte");
            var nextScalar = LLVMBuildAdd(builder, scalarIndex, integer(i64, 1), "next_scalar");

            requireValue(LLVMBuildStore(builder, nextByte, byteSlot), "advanced string byte offset");
            requireValue(LLVMBuildStore(builder, nextScalar, scalarSlot), "advanced string scalar index");
            requireValue(LLVMBuildBr(builder, loop), "string offset loop back edge");

            LLVMPositionBuilderAtEnd(builder, done);
            requireValue(LLVMBuildRet(builder, LLVMBuildLoad2(builder, i64, byteSlot, "byte_offset")), "string byte offset return");

            LLVMPositionBuilderAtEnd(builder, invalid);
            panic(builder, "Sol runtime error: string scalar offset out of bounds.");
        });

        return byteOffset;
    }

    private HostFunction indexFunction() {
        if (index != null) return index;

        var llvmContext = llvmContext();
        var stringType = LlvmStringLowerer.type(llvmContext);
        var i64 = LLVMInt64TypeInContext(llvmContext);
        var i32 = LLVMInt32TypeInContext(llvmContext);

        index = declareInternal(INDEX_NAME, i32, stringType, i64);

        withBuilder(index, builder -> {
            var function = index.value();
            var entry = appendBlock(function, "entry");
            var decode = appendBlock(function, "decode");
            var ascii = appendBlock(function, "ascii");
            var two = appendBlock(function, "two_byte");
            var twoResult = appendBlock(function, "two_byte_result");
            var threeCheck = appendBlock(function, "three_check");
            var three = appendBlock(function, "three_byte");
            var four = appendBlock(function, "four_byte");
            var invalid = appendBlock(function, "invalid");

            LLVMPositionBuilderAtEnd(builder, entry);

            var string = parameter(function, 0, INDEX_NAME);
            var scalarIndex = parameter(function, 1, INDEX_NAME);
            var scalarLength = extract(builder, string, 2, "scalar_length");
            var zero = integer(i64, 0);
            var negative = LLVMBuildICmp(builder, LLVMIntSLT, scalarIndex, zero, "index_negative");
            var pastEnd = LLVMBuildICmp(builder, LLVMIntSGE, scalarIndex, scalarLength, "index_past_end");
            var outOfBounds = LLVMBuildOr(builder, negative, pastEnd, "index_out_of_bounds");

            requireValue(LLVMBuildCondBr(builder, outOfBounds, invalid, decode), "string index validation branch");

            LLVMPositionBuilderAtEnd(builder, decode);

            var data = extract(builder, string, 0, "data");
            var byteIndex = call(builder, byteOffsetFunction(), "byte_index", string, scalarIndex);
            var first = loadByte(builder, data, byteIndex, i32, "first");
            var isAscii = LLVMBuildICmp(builder, LLVMIntULT, first, integer(i32, 0x80), "is_ascii");

            requireValue(LLVMBuildCondBr(builder, isAscii, ascii, two), "ASCII string index branch");

            LLVMPositionBuilderAtEnd(builder, ascii);
            requireValue(LLVMBuildRet(builder, first), "ASCII string index return");

            LLVMPositionBuilderAtEnd(builder, two);

            var isTwo = LLVMBuildICmp(builder, LLVMIntULT, first, integer(i32, 0xE0), "is_two_byte");

            requireValue(LLVMBuildCondBr(builder, isTwo, twoResult, threeCheck), "two-byte string index branch");

            LLVMPositionBuilderAtEnd(builder, twoResult);
            requireValue(LLVMBuildRet(builder, decodeTwo(builder, data, byteIndex, first, i32)), "two-byte string index return");

            LLVMPositionBuilderAtEnd(builder, threeCheck);

            var isThree = LLVMBuildICmp(builder, LLVMIntULT, first, integer(i32, 0xF0), "is_three_byte");

            requireValue(LLVMBuildCondBr(builder, isThree, three, four), "three-byte string index branch");

            LLVMPositionBuilderAtEnd(builder, three);
            requireValue(LLVMBuildRet(builder, decodeThree(builder, data, byteIndex, first, i32)), "three-byte string index return");

            LLVMPositionBuilderAtEnd(builder, four);
            requireValue(LLVMBuildRet(builder, decodeFour(builder, data, byteIndex, first, i32)), "four-byte string index return");

            LLVMPositionBuilderAtEnd(builder, invalid);
            panic(builder, "Sol runtime error: string index out of bounds.");
        });

        return index;
    }

    private HostFunction sliceFunction() {
        if (slice != null) return slice;

        var llvmContext = llvmContext();
        var stringType = LlvmStringLowerer.type(llvmContext);
        var i64 = LLVMInt64TypeInContext(llvmContext);

        slice = declareInternal(SLICE_NAME, stringType, stringType, i64, i64);

        withBuilder(slice, builder -> {
            var function = slice.value();
            var entry = appendBlock(function, "entry");
            var valid = appendBlock(function, "valid");
            var invalid = appendBlock(function, "invalid");

            LLVMPositionBuilderAtEnd(builder, entry);

            var string = parameter(function, 0, SLICE_NAME);
            var start = parameter(function, 1, SLICE_NAME);
            var end = parameter(function, 2, SLICE_NAME);
            var scalarLength = extract(builder, string, 2, "scalar_length");
            var zero = integer(i64, 0);
            var startNegative = LLVMBuildICmp(builder, LLVMIntSLT, start, zero, "start_negative");
            var endNegative = LLVMBuildICmp(builder, LLVMIntSLT, end, zero, "end_negative");
            var reversed = LLVMBuildICmp(builder, LLVMIntSGT, start, end, "range_reversed");
            var pastEnd = LLVMBuildICmp(builder, LLVMIntSGT, end, scalarLength, "end_past_length");
            var negative = LLVMBuildOr(builder, startNegative, endNegative, "range_negative");
            var invalidOrder = LLVMBuildOr(builder, reversed, pastEnd, "range_invalid_order");
            var invalidRange = LLVMBuildOr(builder, negative, invalidOrder, "range_invalid");

            requireValue(LLVMBuildCondBr(builder, invalidRange, invalid, valid), "string slice validation branch");

            LLVMPositionBuilderAtEnd(builder, valid);

            var data = extract(builder, string, 0, "data");
            var startByte = call(builder, byteOffsetFunction(), "start_byte", string, start);
            var endByte = call(builder, byteOffsetFunction(), "end_byte", string, end);
            var slicedData = gep(builder, data, startByte, "slice_data");
            var byteLength = LLVMBuildSub(builder, endByte, startByte, "slice_byte_length");
            var sliceScalars = LLVMBuildSub(builder, end, start, "slice_scalar_length");
            var result = buildString(builder, slicedData, byteLength, sliceScalars, "slice");

            requireValue(LLVMBuildRet(builder, result), "string slice return");

            LLVMPositionBuilderAtEnd(builder, invalid);
            panic(builder, "Sol runtime error: invalid string slice range.");
        });

        return slice;
    }

    private HostFunction substringFunction() {
        if (substring != null) return substring;

        var llvmContext = llvmContext();
        var stringType = LlvmStringLowerer.type(llvmContext);
        var i64 = LLVMInt64TypeInContext(llvmContext);

        substring = declareInternal(SUBSTRING_NAME, stringType, stringType, i64, i64);

        withBuilder(substring, builder -> {
            var function = substring.value();
            var entry = appendBlock(function, "entry");
            var sizeCheck = appendBlock(function, "size_check");
            var valid = appendBlock(function, "valid");
            var invalid = appendBlock(function, "invalid");

            LLVMPositionBuilderAtEnd(builder, entry);

            var string = parameter(function, 0, SUBSTRING_NAME);
            var start = parameter(function, 1, SUBSTRING_NAME);
            var count = parameter(function, 2, SUBSTRING_NAME);
            var zero = integer(i64, 0);
            var startNegative = LLVMBuildICmp(builder, LLVMIntSLT, start, zero, "start_negative");
            var countNegative = LLVMBuildICmp(builder, LLVMIntSLT, count, zero, "count_negative");
            var negative = LLVMBuildOr(builder, startNegative, countNegative, "range_negative");

            requireValue(LLVMBuildCondBr(builder, negative, invalid, sizeCheck), "substring sign validation branch");

            LLVMPositionBuilderAtEnd(builder, sizeCheck);

            var maximum = integer(i64, Long.MAX_VALUE);
            var maximumStart = LLVMBuildSub(builder, maximum, count, "maximum_start");
            var overflows = LLVMBuildICmp(builder, LLVMIntSGT, start, maximumStart, "range_overflows");

            requireValue(LLVMBuildCondBr(builder, overflows, invalid, valid), "substring overflow validation branch");

            LLVMPositionBuilderAtEnd(builder, valid);

            var end = LLVMBuildAdd(builder, start, count, "end");
            var scalarLength = extract(builder, string, 2, "scalar_length");
            var pastEnd = LLVMBuildICmp(builder, LLVMIntSGT, end, scalarLength, "end_past_length");
            var produce = appendBlock(function, "produce");

            requireValue(LLVMBuildCondBr(builder, pastEnd, invalid, produce), "substring bounds validation branch");

            LLVMPositionBuilderAtEnd(builder, produce);
            requireValue(LLVMBuildRet(builder, call(builder, sliceFunction(), "substring", string, start, end)), "substring return");

            LLVMPositionBuilderAtEnd(builder, invalid);
            panic(builder, "Sol runtime error: invalid string substring range.");
        });

        return substring;
    }

    private HostFunction concatFunction() {
        if (concat != null) return concat;

        var llvmContext = llvmContext();
        var stringType = LlvmStringLowerer.type(llvmContext);
        var i64 = LLVMInt64TypeInContext(llvmContext);
        var pointer = LLVMPointerTypeInContext(llvmContext, 0);

        concat = declareInternal(CONCAT_NAME, stringType, stringType, stringType);

        withBuilder(concat, builder -> {
            var function = concat.value();
            var entry = appendBlock(function, "entry");
            var capacityCheck = appendBlock(function, "capacity_check");
            var allocate = appendBlock(function, "allocate");
            var copy = appendBlock(function, "copy");
            var overflow = appendBlock(function, "overflow");
            var allocationFailed = appendBlock(function, "allocation_failed");

            LLVMPositionBuilderAtEnd(builder, entry);

            var left = parameter(function, 0, CONCAT_NAME);
            var right = parameter(function, 1, CONCAT_NAME);
            var leftBytes = extract(builder, left, 1, "left_byte_length");
            var rightBytes = extract(builder, right, 1, "right_byte_length");
            var leftScalars = extract(builder, left, 2, "left_scalar_length");
            var rightScalars = extract(builder, right, 2, "right_scalar_length");
            var maximum = integer(i64, Long.MAX_VALUE);
            var maximumLeftBytes = LLVMBuildSub(builder, maximum, rightBytes, "maximum_left_bytes");
            var maximumLeftScalars = LLVMBuildSub(builder, maximum, rightScalars, "maximum_left_scalars");
            var bytesFit = LLVMBuildICmp(builder, LLVMIntULE, leftBytes, maximumLeftBytes, "byte_lengths_fit");
            var scalarsFit = LLVMBuildICmp(builder, LLVMIntULE, leftScalars, maximumLeftScalars, "scalar_lengths_fit");
            var lengthsFit = LLVMBuildAnd(builder, bytesFit, scalarsFit, "lengths_fit");

            requireValue(LLVMBuildCondBr(builder, lengthsFit, capacityCheck, overflow), "string concatenation overflow branch");

            LLVMPositionBuilderAtEnd(builder, capacityCheck);

            var byteLength = LLVMBuildAdd(builder, leftBytes, rightBytes, "byte_length");
            var hasTerminatorCapacity = LLVMBuildICmp(builder, LLVMIntULT, byteLength, maximum, "has_terminator_capacity");

            requireValue(LLVMBuildCondBr(builder, hasTerminatorCapacity, allocate, overflow), "string concatenation capacity branch");

            LLVMPositionBuilderAtEnd(builder, allocate);

            var capacity = LLVMBuildAdd(builder, byteLength, integer(i64, 1), "capacity");
            var malloc = external("malloc", pointer, i64);
            var data = call(builder, malloc, "data", capacity);
            var missing = LLVMBuildIsNull(builder, data, "allocation_missing");

            requireValue(LLVMBuildCondBr(builder, missing, allocationFailed, copy), "string concatenation allocation branch");

            LLVMPositionBuilderAtEnd(builder, copy);

            var leftData = extract(builder, left, 0, "left_data");
            var rightData = extract(builder, right, 0, "right_data");

            requireValue(LLVMBuildMemCpy(builder, data, 1, leftData, 1, leftBytes), "left string concatenation copy");

            var rightDestination = gep(builder, data, leftBytes, "right_destination");

            requireValue(LLVMBuildMemCpy(builder, rightDestination, 1, rightData, 1, rightBytes), "right string concatenation copy");

            var terminator = gep(builder, data, byteLength, "terminator");

            requireValue(LLVMBuildStore(builder, integer(LLVMInt8TypeInContext(llvmContext), 0), terminator), "string concatenation terminator");

            var scalarLength = LLVMBuildAdd(builder, leftScalars, rightScalars, "scalar_length");
            var result = buildString(builder, data, byteLength, scalarLength, "concatenated");

            requireValue(LLVMBuildRet(builder, result), "string concatenation return");

            LLVMPositionBuilderAtEnd(builder, overflow);
            panic(builder, "Sol runtime error: string concatenation length overflow.");

            LLVMPositionBuilderAtEnd(builder, allocationFailed);
            panic(builder, "Sol runtime error: string concatenation allocation failed.");
        });

        return concat;
    }

    private HostFunction equalFunction() {
        if (equal != null) return equal;

        var llvmContext = llvmContext();
        var stringType = LlvmStringLowerer.type(llvmContext);
        var i64 = LLVMInt64TypeInContext(llvmContext);
        var i32 = LLVMInt32TypeInContext(llvmContext);
        var i1 = LLVMInt1TypeInContext(llvmContext);
        var pointer = LLVMPointerTypeInContext(llvmContext, 0);

        equal = declareInternal(EQUAL_NAME, i1, stringType, stringType);

        withBuilder(equal, builder -> {
            var function = equal.value();
            var entry = appendBlock(function, "entry");
            var sameLength = appendBlock(function, "same_length");
            var compare = appendBlock(function, "compare");
            var trueResult = appendBlock(function, "true");
            var falseResult = appendBlock(function, "false");

            LLVMPositionBuilderAtEnd(builder, entry);

            var left = parameter(function, 0, EQUAL_NAME);
            var right = parameter(function, 1, EQUAL_NAME);
            var leftLength = extract(builder, left, 1, "left_byte_length");
            var rightLength = extract(builder, right, 1, "right_byte_length");
            var lengthsEqual = LLVMBuildICmp(builder, LLVMIntEQ, leftLength, rightLength, "lengths_equal");

            requireValue(LLVMBuildCondBr(builder, lengthsEqual, sameLength, falseResult), "string equality length branch");

            LLVMPositionBuilderAtEnd(builder, sameLength);

            var empty = LLVMBuildICmp(builder, LLVMIntEQ, leftLength, integer(i64, 0), "empty");

            requireValue(LLVMBuildCondBr(builder, empty, trueResult, compare), "empty string equality branch");

            LLVMPositionBuilderAtEnd(builder, compare);

            var leftData = extract(builder, left, 0, "left_data");
            var rightData = extract(builder, right, 0, "right_data");
            var memcmp = external("memcmp", i32, pointer, pointer, i64);
            var comparison = call(builder, memcmp, "comparison", leftData, rightData, leftLength);
            var bytesEqual = LLVMBuildICmp(builder, LLVMIntEQ, comparison, integer(i32, 0), "bytes_equal");

            requireValue(LLVMBuildRet(builder, bytesEqual), "string equality comparison return");

            LLVMPositionBuilderAtEnd(builder, trueResult);
            requireValue(LLVMBuildRet(builder, integer(i1, 1)), "true string equality return");

            LLVMPositionBuilderAtEnd(builder, falseResult);
            requireValue(LLVMBuildRet(builder, integer(i1, 0)), "false string equality return");
        });

        return equal;
    }

    private HostFunction failFunction() {
        if (fail != null) return fail;

        var llvmContext = llvmContext();
        var pointer = LLVMPointerTypeInContext(llvmContext, 0);
        var i32 = LLVMInt32TypeInContext(llvmContext);
        var voidType = LLVMVoidTypeInContext(llvmContext);

        fail = declareInternal(FAIL_NAME, voidType, pointer);

        withBuilder(fail, builder -> {
            var function = fail.value();
            var entry = appendBlock(function, "entry");

            LLVMPositionBuilderAtEnd(builder, entry);

            var message = parameter(function, 0, FAIL_NAME);
            var puts = external("puts", i32, pointer);
            var fflush = external("fflush", i32, pointer);
            var exit = external("exit", voidType, i32);

            call(builder, puts, "", message);
            call(builder, fflush, "", LLVMConstNull(pointer));
            call(builder, exit, "", integer(i32, 70));
            requireValue(LLVMBuildUnreachable(builder), "string runtime failure terminator");
        });

        return fail;
    }

    private LLVMValueRef decodeTwo(
        LLVMBuilderRef builder,
        LLVMValueRef data,
        LLVMValueRef byteIndex,
        LLVMValueRef first,
        LLVMTypeRef i32
    ) {
        var second = loadByte(builder, data, offset(builder, byteIndex, 1), i32, "second");
        var high = LLVMBuildShl(builder, LLVMBuildAnd(builder, first, integer(i32, 0x1F), "first_payload"), integer(i32, 6), "high");
        var low = LLVMBuildAnd(builder, second, integer(i32, 0x3F), "second_payload");

        return LLVMBuildOr(builder, high, low, "code_point");
    }

    private LLVMValueRef decodeThree(
        LLVMBuilderRef builder,
        LLVMValueRef data,
        LLVMValueRef byteIndex,
        LLVMValueRef first,
        LLVMTypeRef i32
    ) {
        var second = loadByte(builder, data, offset(builder, byteIndex, 1), i32, "second");
        var third = loadByte(builder, data, offset(builder, byteIndex, 2), i32, "third");
        var firstPayload = LLVMBuildAnd(builder, first, integer(i32, 0x0F), "first_payload");
        var secondPayload = LLVMBuildAnd(builder, second, integer(i32, 0x3F), "second_payload");
        var thirdPayload = LLVMBuildAnd(builder, third, integer(i32, 0x3F), "third_payload");
        var high = LLVMBuildShl(builder, firstPayload, integer(i32, 12), "high");
        var middle = LLVMBuildShl(builder, secondPayload, integer(i32, 6), "middle");

        return LLVMBuildOr(builder, LLVMBuildOr(builder, high, middle, "high_middle"), thirdPayload, "code_point");
    }

    private LLVMValueRef decodeFour(
        LLVMBuilderRef builder,
        LLVMValueRef data,
        LLVMValueRef byteIndex,
        LLVMValueRef first,
        LLVMTypeRef i32
    ) {
        var second = loadByte(builder, data, offset(builder, byteIndex, 1), i32, "second");
        var third = loadByte(builder, data, offset(builder, byteIndex, 2), i32, "third");
        var fourth = loadByte(builder, data, offset(builder, byteIndex, 3), i32, "fourth");
        var firstPayload = LLVMBuildAnd(builder, first, integer(i32, 0x07), "first_payload");
        var secondPayload = LLVMBuildAnd(builder, second, integer(i32, 0x3F), "second_payload");
        var thirdPayload = LLVMBuildAnd(builder, third, integer(i32, 0x3F), "third_payload");
        var fourthPayload = LLVMBuildAnd(builder, fourth, integer(i32, 0x3F), "fourth_payload");
        var firstPart = LLVMBuildShl(builder, firstPayload, integer(i32, 18), "first_part");
        var secondPart = LLVMBuildShl(builder, secondPayload, integer(i32, 12), "second_part");
        var thirdPart = LLVMBuildShl(builder, thirdPayload, integer(i32, 6), "third_part");
        var firstTwo = LLVMBuildOr(builder, firstPart, secondPart, "first_two");
        var firstThree = LLVMBuildOr(builder, firstTwo, thirdPart, "first_three");

        return LLVMBuildOr(builder, firstThree, fourthPayload, "code_point");
    }

    private LLVMValueRef utf8Width(LLVMBuilderRef builder, LLVMValueRef firstByte, LLVMTypeRef i64) {
        var ascii = LLVMBuildICmp(builder, LLVMIntULT, firstByte, integer(i64, 0x80), "width_ascii");
        var twoByte = LLVMBuildICmp(builder, LLVMIntULT, firstByte, integer(i64, 0xE0), "width_two_byte");
        var threeByte = LLVMBuildICmp(builder, LLVMIntULT, firstByte, integer(i64, 0xF0), "width_three_byte");
        var threeOrFour = LLVMBuildSelect(builder, threeByte, integer(i64, 3), integer(i64, 4), "width_three_or_four");
        var twoOrMore = LLVMBuildSelect(builder, twoByte, integer(i64, 2), threeOrFour, "width_two_or_more");

        return LLVMBuildSelect(builder, ascii, integer(i64, 1), twoOrMore, "utf8_width");
    }

    private LLVMValueRef loadByte(
        LLVMBuilderRef builder,
        LLVMValueRef data,
        LLVMValueRef index,
        LLVMTypeRef resultType,
        String name
    ) {
        var i8 = LLVMInt8TypeInContext(llvmContext());
        var address = gep(builder, data, index, name + "_address");
        var byteValue = LLVMBuildLoad2(builder, i8, address, name + "_byte");
        var extended = LLVMBuildZExt(builder, byteValue, resultType, name);

        requireValue(byteValue, "UTF-8 byte load");
        requireValue(extended, "UTF-8 byte extension");

        return extended;
    }

    private LLVMValueRef offset(LLVMBuilderRef builder, LLVMValueRef base, long amount) {
        return LLVMBuildAdd(builder, base, integer(LLVMInt64TypeInContext(llvmContext()), amount), "byte_offset_%d".formatted(amount));
    }

    private LLVMValueRef gep(LLVMBuilderRef builder, LLVMValueRef data, LLVMValueRef index, String name) {
        final LLVMValueRef address;

        try (var indices = new PointerPointer<LLVMValueRef>(1)) {
            indices.put(0, index);
            address = LLVMBuildGEP2(builder, LLVMInt8TypeInContext(llvmContext()), data, indices, 1, name);
        }

        requireValue(address, "UTF-8 byte address");

        return address;
    }

    private LLVMValueRef buildString(
        LLVMBuilderRef builder,
        LLVMValueRef data,
        LLVMValueRef byteLength,
        LLVMValueRef scalarLength,
        String name
    ) {
        var type = LlvmStringLowerer.type(llvmContext());
        var withData = LLVMBuildInsertValue(builder, LLVMGetUndef(type), data, 0, name + "_data");
        var withBytes = LLVMBuildInsertValue(builder, withData, byteLength, 1, name + "_bytes");
        var complete = LLVMBuildInsertValue(builder, withBytes, scalarLength, 2, name);

        requireValue(complete, "native Sol string value");

        return complete;
    }

    private LLVMValueRef extract(LLVMBuilderRef builder, LLVMValueRef string, int index, String name) {
        var value = LLVMBuildExtractValue(builder, string, index, name);

        requireValue(value, "native Sol string field '%s'".formatted(name));

        return value;
    }

    private void panic(LLVMBuilderRef builder, String message) {
        var diagnostic = LLVMBuildGlobalStringPtr(
            builder,
            message,
            "sol.string.runtime.error.%d".formatted(diagnosticIndex++)
        );

        requireValue(diagnostic, "string runtime diagnostic");
        call(builder, failFunction(), "", diagnostic);
        requireValue(LLVMBuildUnreachable(builder), "string runtime diagnostic terminator");
    }

    private HostFunction declareInternal(String name, LLVMTypeRef returnType, LLVMTypeRef... parameterTypes) {
        var function = declare(name, returnType, parameterTypes);

        LLVMSetLinkage(function.value(), LLVMInternalLinkage);

        return function;
    }

    private HostFunction external(String name, LLVMTypeRef returnType, LLVMTypeRef... parameterTypes) {
        return declare(name, returnType, parameterTypes);
    }

    private HostFunction declare(String name, LLVMTypeRef returnType, LLVMTypeRef... parameterTypes) {
        final LLVMTypeRef functionType;

        try (var nativeParameters = new PointerPointer<LLVMTypeRef>(parameterTypes)) {
            functionType = LLVMFunctionType(returnType, nativeParameters, parameterTypes.length, 0);
        }

        requireValue(functionType, "string runtime function type '%s'".formatted(name));

        var function = LLVMGetNamedFunction(context.module().moduleHandle(), name);

        if (Pointer.isNull(function)) function = LLVMAddFunction(context.module().moduleHandle(), name, functionType);

        requireValue(function, "string runtime function '%s'".formatted(name));

        return new HostFunction(functionType, function);
    }

    private LLVMValueRef call(LLVMBuilderRef builder, HostFunction function, String name, LLVMValueRef... arguments) {
        final LLVMValueRef lowered;

        try (var nativeArguments = new PointerPointer<LLVMValueRef>(arguments)) {
            lowered = LLVMBuildCall2(builder, function.type(), function.value(), nativeArguments, arguments.length, name);
        }

        requireValue(lowered, "string runtime call");

        return lowered;
    }

    private LLVMValueRef parameter(LLVMValueRef function, int index, String sourceName) {
        var parameter = LLVMGetParam(function, index);

        requireValue(parameter, "parameter %d of string runtime function '%s'".formatted(index, sourceName));

        return parameter;
    }

    private LLVMBasicBlockRef appendBlock(LLVMValueRef function, String name) {
        var block = LLVMAppendBasicBlockInContext(llvmContext(), function, name);

        requireValue(block, "string runtime block '%s'".formatted(name));

        return block;
    }

    private LLVMValueRef integer(LLVMTypeRef type, long value) {
        var constant = LLVMConstInt(type, value, value < 0 ? 1 : 0);

        requireValue(constant, "string runtime integer constant");

        return constant;
    }

    private LLVMContextRef llvmContext() {
        return context.module().contextHandle();
    }

    private void withBuilder(HostFunction function, BuilderAction action) {
        var builder = LLVMCreateBuilderInContext(llvmContext());

        requireValue(builder, "builder for string runtime function");

        try {
            action.lower(builder);
        } finally {
            LLVMDisposeBuilder(builder);
            builder.setNull();
        }
    }

    private static void requireValue(Pointer value, String description) {
        if (Pointer.isNull(value)) throw new LlvmBackendException("LLVM failed to create %s.".formatted(description));
    }

    @FunctionalInterface
    private interface BuilderAction {
        void lower(LLVMBuilderRef builder);
    }

    private record HostFunction(LLVMTypeRef type, LLVMValueRef value) {
        private HostFunction {
            Objects.requireNonNull(type, "String runtime function type must not be null.");
            Objects.requireNonNull(value, "String runtime function value must not be null.");

            if (Pointer.isNull(type) || Pointer.isNull(value))
                throw new IllegalArgumentException("String runtime function pointers must not be null.");
        }
    }
}
