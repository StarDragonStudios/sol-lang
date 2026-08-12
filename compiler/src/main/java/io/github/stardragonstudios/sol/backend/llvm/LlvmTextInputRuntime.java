package io.github.stardragonstudios.sol.backend.llvm;

import io.github.stardragonstudios.sol.ir.IrFunction;
import io.github.stardragonstudios.sol.ir.PrimitiveIrType;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.util.Objects;

import static org.bytedeco.llvm.global.LLVM.*;

/** Native, process-lifetime storage and strict UTF-8 validation for text input. */
final class LlvmTextInputRuntime {
    private static final long INITIAL_CAPACITY = 256;
    private static final long VALIDATION_PADDING = 4;
    private static final int EOF = -1;

    private final LlvmProgramLoweringContext context;
    private int diagnosticIndex;

    LlvmTextInputRuntime(LlvmProgramLoweringContext context) {
        this.context = Objects.requireNonNull(context, "LLVM text-input context must not be null.");
    }

    void lowerReadText(IrFunction function) {
        validateReadText(function);

        var pointer = LLVMPointerType(LLVMInt8TypeInContext(llvmContext()), 0);
        var i32 = LLVMInt32TypeInContext(llvmContext());
        var i64 = LLVMInt64TypeInContext(llvmContext());
        var fopen = external("fopen", pointer, pointer, pointer);
        var fgetc = external("fgetc", i32, pointer);
        var ferror = external("ferror", i32, pointer);
        var fclose = external("fclose", i32, pointer);

        withBuilder(function, builder -> {
            var handle = context.function(function.id()).value();
            var entry = block(handle, "file.read.entry");
            var opened = block(handle, "file.read.opened");
            var openFailed = block(handle, "file.read.open_failed");
            var loop = block(handle, "file.read.loop");
            var byteRead = block(handle, "file.read.byte");
            var ended = block(handle, "file.read.ended");
            var readFailed = block(handle, "file.read.failed");
            var finish = block(handle, "file.read.finish");

            LLVMPositionBuilderAtEnd(builder, entry);
            var path = parameter(handle, 0, function.name());
            var pathData = extract(builder, path, 0, "path_data");
            var pathLength = extract(builder, path, 1, "path_length");
            var cPath = cString(builder, pathData, pathLength);
            var mode = LLVMBuildGlobalStringPtr(builder, "rb", "sol.file.read.mode");
            var file = call(builder, fopen, "file", cPath, mode);
            var missing = LLVMBuildIsNull(builder, file, "open_failed");
            requireValue(LLVMBuildCondBr(builder, missing, openFailed, opened), "file read open branch");

            LLVMPositionBuilderAtEnd(builder, openFailed);
            panic(builder, "Sol runtime error: std.file.read_text could not open the file.");

            LLVMPositionBuilderAtEnd(builder, opened);
            var buffer = newBuffer(builder, i64);
            requireValue(LLVMBuildBr(builder, loop), "file read loop entry");

            LLVMPositionBuilderAtEnd(builder, loop);
            var character = call(builder, fgetc, "character", file);
            var atEnd = LLVMBuildICmp(builder, LLVMIntEQ, character, integer(i32, EOF), "at_end");
            requireValue(LLVMBuildCondBr(builder, atEnd, ended, byteRead), "file read byte branch");

            LLVMPositionBuilderAtEnd(builder, byteRead);
            appendByte(builder, buffer, character, i64);
            requireValue(LLVMBuildBr(builder, loop), "file read loop back edge");

            LLVMPositionBuilderAtEnd(builder, ended);
            var error = call(builder, ferror, "read_error", file);
            var failed = LLVMBuildICmp(builder, LLVMIntNE, error, integer(i32, 0), "read_failed");
            requireValue(LLVMBuildCondBr(builder, failed, readFailed, finish), "file read completion branch");

            LLVMPositionBuilderAtEnd(builder, readFailed);
            call(builder, fclose, "", file);
            panic(builder, "Sol runtime error: std.file.read_text failed while reading the file.");

            LLVMPositionBuilderAtEnd(builder, finish);
            var closeResult = call(builder, fclose, "close_result", file);
            var closeFailed = LLVMBuildICmp(builder, LLVMIntNE, closeResult, integer(i32, 0), "close_failed");
            var closed = block(handle, "file.read.closed");
            var closeError = block(handle, "file.read.close_failed");
            requireValue(LLVMBuildCondBr(builder, closeFailed, closeError, closed), "file read close branch");

            LLVMPositionBuilderAtEnd(builder, closeError);
            panic(builder, "Sol runtime error: std.file.read_text failed while closing the file.");

            LLVMPositionBuilderAtEnd(builder, closed);
            requireValue(LLVMBuildRet(builder, finishString(builder, buffer)), "file text return");
        });
    }

    void lowerReadLine(IrFunction function) {
        validateReadLine(function);

        var i32 = LLVMInt32TypeInContext(llvmContext());
        var i64 = LLVMInt64TypeInContext(llvmContext());
        var getchar = external("getchar", i32);

        withBuilder(function, builder -> {
            var handle = context.function(function.id()).value();
            var entry = block(handle, "console.read.entry");
            var loop = block(handle, "console.read.loop");
            var inspect = block(handle, "console.read.inspect");
            var append = block(handle, "console.read.append");
            var finish = block(handle, "console.read.finish");
            var finishUnterminated = block(handle, "console.read.finish_unterminated");
            var eof = block(handle, "console.read.eof");
            var eofWithData = block(handle, "console.read.eof_with_data");
            var eofWithoutData = block(handle, "console.read.eof_without_data");

            LLVMPositionBuilderAtEnd(builder, entry);
            var buffer = newBuffer(builder, i64);
            requireValue(LLVMBuildBr(builder, loop), "console input loop entry");

            LLVMPositionBuilderAtEnd(builder, loop);
            var character = call(builder, getchar, "character");
            var atEnd = LLVMBuildICmp(builder, LLVMIntEQ, character, integer(i32, EOF), "at_end");
            requireValue(LLVMBuildCondBr(builder, atEnd, eof, inspect), "console EOF branch");

            LLVMPositionBuilderAtEnd(builder, inspect);
            var newline = LLVMBuildICmp(builder, LLVMIntEQ, character, integer(i32, '\n'), "newline");
            requireValue(LLVMBuildCondBr(builder, newline, finish, append), "console newline branch");

            LLVMPositionBuilderAtEnd(builder, append);
            appendByte(builder, buffer, character, i64);
            requireValue(LLVMBuildBr(builder, loop), "console input loop back edge");

            LLVMPositionBuilderAtEnd(builder, eof);
            var length = loadLength(builder, buffer, i64);
            var empty = LLVMBuildICmp(builder, LLVMIntEQ, length, integer(i64, 0), "empty_at_eof");
            requireValue(LLVMBuildCondBr(builder, empty, eofWithoutData, eofWithData), "console EOF data branch");

            LLVMPositionBuilderAtEnd(builder, eofWithoutData);
            panic(builder, "Sol runtime error: std.console.read_line reached EOF before a line was available.");

            LLVMPositionBuilderAtEnd(builder, eofWithData);
            requireValue(LLVMBuildBr(builder, finishUnterminated), "unterminated final line branch");

            LLVMPositionBuilderAtEnd(builder, finish);
            trimTrailingCarriageReturn(builder, buffer, i64);
            requireValue(LLVMBuildRet(builder, finishString(builder, buffer)), "console line return");

            LLVMPositionBuilderAtEnd(builder, finishUnterminated);
            requireValue(LLVMBuildRet(builder, finishString(builder, buffer)), "unterminated console line return");
        });
    }

    private Buffer newBuffer(LLVMBuilderRef builder, LLVMTypeRef i64) {
        var pointer = LLVMPointerType(LLVMInt8TypeInContext(llvmContext()), 0);
        var malloc = external("malloc", pointer, i64);
        var dataSlot = LLVMBuildAlloca(builder, pointer, "input_data");
        var lengthSlot = LLVMBuildAlloca(builder, i64, "input_length");
        var capacitySlot = LLVMBuildAlloca(builder, i64, "input_capacity");
        var capacity = integer(i64, INITIAL_CAPACITY);
        var allocationSize = LLVMBuildAdd(builder, capacity, integer(i64, VALIDATION_PADDING), "input_allocation_size");
        var data = call(builder, malloc, "input_buffer", allocationSize);
        var allocationFailed = LLVMBuildIsNull(builder, data, "allocation_failed");
        var ready = block(LLVMGetBasicBlockParent(LLVMGetInsertBlock(builder)), "input.allocated");
        var failed = block(LLVMGetBasicBlockParent(LLVMGetInsertBlock(builder)), "input.allocation_failed");

        requireValue(LLVMBuildCondBr(builder, allocationFailed, failed, ready), "input allocation branch");
        LLVMPositionBuilderAtEnd(builder, failed);
        panic(builder, "Sol runtime error: text input allocation failed.");
        LLVMPositionBuilderAtEnd(builder, ready);
        requireValue(LLVMBuildStore(builder, data, dataSlot), "input data initialization");
        requireValue(LLVMBuildStore(builder, integer(i64, 0), lengthSlot), "input length initialization");
        requireValue(LLVMBuildStore(builder, capacity, capacitySlot), "input capacity initialization");

        return new Buffer(dataSlot, lengthSlot, capacitySlot);
    }

    private void appendByte(LLVMBuilderRef builder, Buffer buffer, LLVMValueRef character, LLVMTypeRef i64) {
        var handle = LLVMGetBasicBlockParent(LLVMGetInsertBlock(builder));
        var length = loadLength(builder, buffer, i64);
        var capacity = LLVMBuildLoad2(builder, i64, buffer.capacitySlot(), "capacity");
        var full = LLVMBuildICmp(builder, LLVMIntEQ, length, capacity, "buffer_full");
        var grow = block(handle, "input.grow");
        var store = block(handle, "input.store");

        requireValue(LLVMBuildCondBr(builder, full, grow, store), "input capacity branch");
        LLVMPositionBuilderAtEnd(builder, grow);

        var pointer = LLVMPointerType(LLVMInt8TypeInContext(llvmContext()), 0);
        var realloc = external("realloc", pointer, pointer, i64);
        var canGrow = LLVMBuildICmp(
            builder,
            LLVMIntULE,
            capacity,
            integer(i64, (Long.MAX_VALUE - VALIDATION_PADDING) / 2),
            "capacity_can_grow"
        );
        var resize = block(handle, "input.resize");
        var overflow = block(handle, "input.capacity_overflow");

        requireValue(LLVMBuildCondBr(builder, canGrow, resize, overflow), "input capacity overflow branch");
        LLVMPositionBuilderAtEnd(builder, overflow);
        panic(builder, "Sol runtime error: text input is too large.");
        LLVMPositionBuilderAtEnd(builder, resize);

        var current = LLVMBuildLoad2(builder, pointer, buffer.dataSlot(), "current_data");
        var nextCapacity = LLVMBuildMul(builder, capacity, integer(i64, 2), "next_capacity");
        var allocationSize = LLVMBuildAdd(builder, nextCapacity, integer(i64, VALIDATION_PADDING), "resized_allocation_size");
        var resized = call(builder, realloc, "resized_data", current, allocationSize);
        var failed = LLVMBuildIsNull(builder, resized, "resize_failed");
        var grown = block(handle, "input.grown");
        var resizeFailed = block(handle, "input.resize_failed");

        requireValue(LLVMBuildCondBr(builder, failed, resizeFailed, grown), "input resize branch");
        LLVMPositionBuilderAtEnd(builder, resizeFailed);
        panic(builder, "Sol runtime error: text input allocation failed.");
        LLVMPositionBuilderAtEnd(builder, grown);
        requireValue(LLVMBuildStore(builder, resized, buffer.dataSlot()), "resized input data store");
        requireValue(LLVMBuildStore(builder, nextCapacity, buffer.capacitySlot()), "resized input capacity store");
        requireValue(LLVMBuildBr(builder, store), "input resized store branch");

        LLVMPositionBuilderAtEnd(builder, store);
        var data = LLVMBuildLoad2(builder, pointer, buffer.dataSlot(), "data");
        var address = gep(builder, data, length, "byte_address");
        var byteValue = LLVMBuildTrunc(builder, character, LLVMInt8TypeInContext(llvmContext()), "byte");

        requireValue(LLVMBuildStore(builder, byteValue, address), "input byte store");
        requireValue(LLVMBuildStore(builder, LLVMBuildAdd(builder, length, integer(i64, 1), "next_length"), buffer.lengthSlot()), "input length update");
    }

    private void trimTrailingCarriageReturn(LLVMBuilderRef builder, Buffer buffer, LLVMTypeRef i64) {
        var handle = LLVMGetBasicBlockParent(LLVMGetInsertBlock(builder));
        var length = loadLength(builder, buffer, i64);
        var nonEmpty = LLVMBuildICmp(builder, LLVMIntSGT, length, integer(i64, 0), "line_non_empty");
        var inspect = block(handle, "console.read.inspect_cr");
        var done = block(handle, "console.read.trimmed");

        requireValue(LLVMBuildCondBr(builder, nonEmpty, inspect, done), "line CR inspection branch");
        LLVMPositionBuilderAtEnd(builder, inspect);
        var pointer = LLVMPointerType(LLVMInt8TypeInContext(llvmContext()), 0);
        var data = LLVMBuildLoad2(builder, pointer, buffer.dataSlot(), "line_data");
        var lastIndex = LLVMBuildSub(builder, length, integer(i64, 1), "last_index");
        var last = LLVMBuildLoad2(builder, LLVMInt8TypeInContext(llvmContext()), gep(builder, data, lastIndex, "last_byte_address"), "last_byte");
        var carriageReturn = LLVMBuildICmp(builder, LLVMIntEQ, last, integer(LLVMInt8TypeInContext(llvmContext()), '\r'), "trailing_cr");
        var trim = block(handle, "console.read.trim_cr");

        requireValue(LLVMBuildCondBr(builder, carriageReturn, trim, done), "line CR branch");
        LLVMPositionBuilderAtEnd(builder, trim);
        requireValue(LLVMBuildStore(builder, lastIndex, buffer.lengthSlot()), "trimmed line length");
        requireValue(LLVMBuildBr(builder, done), "trimmed line branch");
        LLVMPositionBuilderAtEnd(builder, done);
    }

    private LLVMValueRef finishString(LLVMBuilderRef builder, Buffer buffer) {
        var i64 = LLVMInt64TypeInContext(llvmContext());
        var pointer = LLVMPointerType(LLVMInt8TypeInContext(llvmContext()), 0);
        var data = LLVMBuildLoad2(builder, pointer, buffer.dataSlot(), "result_data");
        var length = loadLength(builder, buffer, i64);
        var scalarLength = validateUtf8(builder, data, length);
        var type = LlvmStringLowerer.type(llvmContext());
        var withData = LLVMBuildInsertValue(builder, LLVMGetUndef(type), data, 0, "input_string_data");
        var withBytes = LLVMBuildInsertValue(builder, withData, length, 1, "input_string_bytes");
        var result = LLVMBuildInsertValue(builder, withBytes, scalarLength, 2, "input_string");

        requireValue(result, "input string value");
        return result;
    }

    /* Strict RFC 3629 validation. Returns the Unicode scalar count. */
    private LLVMValueRef validateUtf8(LLVMBuilderRef builder, LLVMValueRef data, LLVMValueRef length) {
        var i8 = LLVMInt8TypeInContext(llvmContext());
        var i32 = LLVMInt32TypeInContext(llvmContext());
        var i64 = LLVMInt64TypeInContext(llvmContext());
        var handle = LLVMGetBasicBlockParent(LLVMGetInsertBlock(builder));
        var indexSlot = LLVMBuildAlloca(builder, i64, "utf8_index");
        var countSlot = LLVMBuildAlloca(builder, i64, "utf8_count");
        var loop = block(handle, "utf8.loop");
        var inspect = block(handle, "utf8.inspect");
        var ascii = block(handle, "utf8.ascii");
        var multibyte = block(handle, "utf8.multibyte");
        var valid = block(handle, "utf8.valid");
        var invalid = block(handle, "utf8.invalid");
        var done = block(handle, "utf8.done");

        requireValue(LLVMBuildStore(builder, integer(i64, 0), indexSlot), "UTF-8 index initialization");
        requireValue(LLVMBuildStore(builder, integer(i64, 0), countSlot), "UTF-8 count initialization");
        requireValue(LLVMBuildBr(builder, loop), "UTF-8 loop entry");

        LLVMPositionBuilderAtEnd(builder, loop);
        var index = LLVMBuildLoad2(builder, i64, indexSlot, "utf8_index_value");
        var hasMore = LLVMBuildICmp(builder, LLVMIntULT, index, length, "utf8_has_more");
        requireValue(LLVMBuildCondBr(builder, hasMore, inspect, done), "UTF-8 loop branch");

        LLVMPositionBuilderAtEnd(builder, inspect);
        var first = LLVMBuildZExt(builder, LLVMBuildLoad2(builder, i8, gep(builder, data, index, "utf8_first_address"), "utf8_first_byte"), i32, "utf8_first");
        var isAscii = LLVMBuildICmp(builder, LLVMIntULT, first, integer(i32, 0x80), "utf8_is_ascii");
        requireValue(LLVMBuildCondBr(builder, isAscii, ascii, multibyte), "UTF-8 ASCII branch");

        LLVMPositionBuilderAtEnd(builder, ascii);
        advanceUtf8(builder, indexSlot, countSlot, index, 1, loop, i64);

        LLVMPositionBuilderAtEnd(builder, multibyte);
        var width = utf8Width(builder, first, i32);
        var widthValid = LLVMBuildICmp(builder, LLVMIntNE, width, integer(i32, 0), "utf8_width_valid");
        var width64 = LLVMBuildZExt(builder, width, i64, "utf8_width64");
        var end = LLVMBuildAdd(builder, index, width64, "utf8_end");
        var fits = LLVMBuildICmp(builder, LLVMIntULE, end, length, "utf8_fits");
        var headerValid = LLVMBuildAnd(builder, widthValid, fits, "utf8_header_valid");
        var continuationCheck = block(handle, "utf8.continuations");
        requireValue(LLVMBuildCondBr(builder, headerValid, continuationCheck, invalid), "UTF-8 header branch");

        LLVMPositionBuilderAtEnd(builder, continuationCheck);
        var continuationValid = validateContinuations(builder, data, index, first, width, i32, i64);
        requireValue(LLVMBuildCondBr(builder, continuationValid, valid, invalid), "UTF-8 continuation branch");

        LLVMPositionBuilderAtEnd(builder, valid);
        var validIndex = LLVMBuildLoad2(builder, i64, indexSlot, "utf8_valid_index");
        var validWidth = LLVMBuildZExt(builder, width, i64, "utf8_valid_width");
        requireValue(LLVMBuildStore(builder, LLVMBuildAdd(builder, validIndex, validWidth, "utf8_next_index"), indexSlot), "UTF-8 multibyte index update");
        incrementCount(builder, countSlot, i64);
        requireValue(LLVMBuildBr(builder, loop), "UTF-8 multibyte loop branch");

        LLVMPositionBuilderAtEnd(builder, invalid);
        panic(builder, "Sol runtime error: text input is not valid UTF-8.");

        LLVMPositionBuilderAtEnd(builder, done);
        return LLVMBuildLoad2(builder, i64, countSlot, "utf8_scalar_count");
    }

    private LLVMValueRef utf8Width(LLVMBuilderRef builder, LLVMValueRef first, LLVMTypeRef i32) {
        var two = between(builder, first, 0xC2, 0xDF, i32, "utf8_two");
        var three = between(builder, first, 0xE0, 0xEF, i32, "utf8_three");
        var four = between(builder, first, 0xF0, 0xF4, i32, "utf8_four");
        return LLVMBuildSelect(builder, two, integer(i32, 2), LLVMBuildSelect(builder, three, integer(i32, 3), LLVMBuildSelect(builder, four, integer(i32, 4), integer(i32, 0), "utf8_width_four"), "utf8_width_three"), "utf8_width");
    }

    private LLVMValueRef validateContinuations(LLVMBuilderRef builder, LLVMValueRef data, LLVMValueRef index, LLVMValueRef first, LLVMValueRef width, LLVMTypeRef i32, LLVMTypeRef i64) {
        var second = byteAt(builder, data, index, 1, i32, i64);
        var secondContinuation = between(builder, second, 0x80, 0xBF, i32, "utf8_second_continuation");
        var e0 = LLVMBuildICmp(builder, LLVMIntEQ, first, integer(i32, 0xE0), "utf8_e0");
        var ed = LLVMBuildICmp(builder, LLVMIntEQ, first, integer(i32, 0xED), "utf8_ed");
        var f0 = LLVMBuildICmp(builder, LLVMIntEQ, first, integer(i32, 0xF0), "utf8_f0");
        var f4 = LLVMBuildICmp(builder, LLVMIntEQ, first, integer(i32, 0xF4), "utf8_f4");
        var secondMinimum = LLVMBuildSelect(builder, e0, integer(i32, 0xA0), LLVMBuildSelect(builder, f0, integer(i32, 0x90), integer(i32, 0x80), "utf8_second_min_f0"), "utf8_second_min");
        var secondMaximum = LLVMBuildSelect(builder, ed, integer(i32, 0x9F), LLVMBuildSelect(builder, f4, integer(i32, 0x8F), integer(i32, 0xBF), "utf8_second_max_f4"), "utf8_second_max");
        var secondSpecial = LLVMBuildAnd(builder, LLVMBuildICmp(builder, LLVMIntUGE, second, secondMinimum, "utf8_second_minimum"), LLVMBuildICmp(builder, LLVMIntULE, second, secondMaximum, "utf8_second_maximum"), "utf8_second_special");
        var secondValid = LLVMBuildAnd(builder, secondContinuation, secondSpecial, "utf8_second_valid");
        var needsThird = LLVMBuildICmp(builder, LLVMIntUGE, width, integer(i32, 3), "utf8_needs_third");
        var third = byteAt(builder, data, index, 2, i32, i64);
        var thirdValid = between(builder, third, 0x80, 0xBF, i32, "utf8_third_valid");
        var thirdResult = LLVMBuildSelect(builder, needsThird, thirdValid, integer(LLVMInt1TypeInContext(llvmContext()), 1), "utf8_third_result");
        var needsFourth = LLVMBuildICmp(builder, LLVMIntEQ, width, integer(i32, 4), "utf8_needs_fourth");
        var fourth = byteAt(builder, data, index, 3, i32, i64);
        var fourthValid = between(builder, fourth, 0x80, 0xBF, i32, "utf8_fourth_valid");
        var fourthResult = LLVMBuildSelect(builder, needsFourth, fourthValid, integer(LLVMInt1TypeInContext(llvmContext()), 1), "utf8_fourth_result");
        return LLVMBuildAnd(builder, secondValid, LLVMBuildAnd(builder, thirdResult, fourthResult, "utf8_tail_valid"), "utf8_sequence_valid");
    }

    private LLVMValueRef byteAt(LLVMBuilderRef builder, LLVMValueRef data, LLVMValueRef index, long offset, LLVMTypeRef i32, LLVMTypeRef i64) {
        var address = gep(builder, data, LLVMBuildAdd(builder, index, integer(i64, offset), "utf8_offset"), "utf8_byte_address");
        return LLVMBuildZExt(builder, LLVMBuildLoad2(builder, LLVMInt8TypeInContext(llvmContext()), address, "utf8_byte"), i32, "utf8_byte_value");
    }

    private LLVMValueRef between(LLVMBuilderRef builder, LLVMValueRef value, long minimum, long maximum, LLVMTypeRef type, String name) {
        var atLeast = LLVMBuildICmp(builder, LLVMIntUGE, value, integer(type, minimum), name + "_minimum");
        var atMost = LLVMBuildICmp(builder, LLVMIntULE, value, integer(type, maximum), name + "_maximum");
        return LLVMBuildAnd(builder, atLeast, atMost, name);
    }

    private void advanceUtf8(LLVMBuilderRef builder, LLVMValueRef indexSlot, LLVMValueRef countSlot, LLVMValueRef index, long width, LLVMBasicBlockRef loop, LLVMTypeRef i64) {
        requireValue(LLVMBuildStore(builder, LLVMBuildAdd(builder, index, integer(i64, width), "utf8_ascii_next"), indexSlot), "UTF-8 ASCII index update");
        incrementCount(builder, countSlot, i64);
        requireValue(LLVMBuildBr(builder, loop), "UTF-8 ASCII loop branch");
    }

    private void incrementCount(LLVMBuilderRef builder, LLVMValueRef countSlot, LLVMTypeRef i64) {
        var count = LLVMBuildLoad2(builder, i64, countSlot, "utf8_count_value");
        requireValue(LLVMBuildStore(builder, LLVMBuildAdd(builder, count, integer(i64, 1), "utf8_next_count"), countSlot), "UTF-8 count update");
    }

    private LLVMValueRef cString(LLVMBuilderRef builder, LLVMValueRef data, LLVMValueRef length) {
        var i8 = LLVMInt8TypeInContext(llvmContext());
        var i64 = LLVMInt64TypeInContext(llvmContext());
        var capacity = LLVMBuildAdd(builder, length, integer(i64, 1), "path_capacity");
        var buffer = LLVMBuildArrayAlloca(builder, i8, capacity, "path_buffer");
        requireValue(LLVMBuildMemCpy(builder, buffer, 1, data, 1, length), "path copy");
        requireValue(LLVMBuildStore(builder, integer(i8, 0), gep(builder, buffer, length, "path_terminator")), "path terminator");
        return buffer;
    }

    private LLVMValueRef loadLength(LLVMBuilderRef builder, Buffer buffer, LLVMTypeRef i64) {
        return LLVMBuildLoad2(builder, i64, buffer.lengthSlot(), "length");
    }

    private LLVMValueRef gep(LLVMBuilderRef builder, LLVMValueRef data, LLVMValueRef index, String name) {
        final LLVMValueRef address;
        try (var indices = new PointerPointer<LLVMValueRef>(1)) {
            indices.put(0, index);
            address = LLVMBuildGEP2(builder, LLVMInt8TypeInContext(llvmContext()), data, indices, 1, name);
        }
        requireValue(address, name);
        return address;
    }

    private LLVMValueRef extract(LLVMBuilderRef builder, LLVMValueRef value, int index, String name) {
        var result = LLVMBuildExtractValue(builder, value, index, name);
        requireValue(result, name);
        return result;
    }

    private LLVMValueRef parameter(LLVMValueRef function, int index, String name) {
        var result = LLVMGetParam(function, index);
        requireValue(result, "parameter %d of '%s'".formatted(index, name));
        return result;
    }

    private void panic(LLVMBuilderRef builder, String message) {
        var puts = external("puts", LLVMInt32TypeInContext(llvmContext()), LLVMPointerType(LLVMInt8TypeInContext(llvmContext()), 0));
        var exit = external("exit", LLVMVoidTypeInContext(llvmContext()), LLVMInt32TypeInContext(llvmContext()));
        var diagnostic = LLVMBuildGlobalStringPtr(builder, message, "sol.input.error.%d".formatted(diagnosticIndex++));
        call(builder, puts, "", diagnostic);
        call(builder, exit, "", integer(LLVMInt32TypeInContext(llvmContext()), 70));
        requireValue(LLVMBuildUnreachable(builder), "input failure terminator");
    }

    private HostFunction external(String name, LLVMTypeRef returnType, LLVMTypeRef... parameterTypes) {
        final LLVMTypeRef functionType;
        try (var nativeParameters = new PointerPointer<LLVMTypeRef>(parameterTypes)) {
            functionType = LLVMFunctionType(returnType, nativeParameters, parameterTypes.length, 0);
        }
        var function = LLVMGetNamedFunction(context.module().moduleHandle(), name);
        if (Pointer.isNull(function)) function = LLVMAddFunction(context.module().moduleHandle(), name, functionType);
        requireValue(functionType, "host function type '%s'".formatted(name));
        requireValue(function, "host function '%s'".formatted(name));
        return new HostFunction(functionType, function);
    }

    private LLVMValueRef call(LLVMBuilderRef builder, HostFunction function, String name, LLVMValueRef... arguments) {
        final LLVMValueRef result;
        try (var nativeArguments = new PointerPointer<LLVMValueRef>(arguments)) {
            result = LLVMBuildCall2(builder, function.type(), function.value(), nativeArguments, arguments.length, name);
        }
        requireValue(result, "host text-input call");
        return result;
    }

    private LLVMBasicBlockRef block(LLVMValueRef function, String name) {
        var result = LLVMAppendBasicBlockInContext(llvmContext(), function, name);
        requireValue(result, "text-input block '%s'".formatted(name));
        return result;
    }

    private LLVMValueRef integer(LLVMTypeRef type, long value) {
        return LLVMConstInt(type, value, value < 0 ? 1 : 0);
    }

    private LLVMContextRef llvmContext() {
        return context.module().contextHandle();
    }

    private void withBuilder(IrFunction function, BuilderAction action) {
        var builder = LLVMCreateBuilderInContext(llvmContext());
        requireValue(builder, "text-input builder");
        try {
            action.lower(builder);
        } finally {
            LLVMDisposeBuilder(builder);
            builder.setNull();
        }
    }

    private static void validateReadText(IrFunction function) {
        if (function.hasBody() || function.parameters().size() != 1 || function.parameters().getFirst().type() != PrimitiveIrType.STRING || function.returnType() != PrimitiveIrType.STRING)
            throw new LlvmBackendException("Standard file function 'read_text' must be bodyless with signature '(string) -> string'.");
    }

    private static void validateReadLine(IrFunction function) {
        if (function.hasBody() || !function.parameters().isEmpty() || function.returnType() != PrimitiveIrType.STRING)
            throw new LlvmBackendException("Standard console function 'read_line' must be bodyless with signature '() -> string'.");
    }

    private static void requireValue(Pointer value, String description) {
        if (Pointer.isNull(value)) throw new LlvmBackendException("LLVM failed to create %s.".formatted(description));
    }

    private record Buffer(LLVMValueRef dataSlot, LLVMValueRef lengthSlot, LLVMValueRef capacitySlot) {}
    private record HostFunction(LLVMTypeRef type, LLVMValueRef value) {}

    @FunctionalInterface
    private interface BuilderAction {
        void lower(LLVMBuilderRef builder);
    }
}
