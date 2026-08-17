#include "selfhost.h"

#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void sol_runtime_panic(const char *message) {
    fputs(message, stdout);
    fputc('\n', stdout);
    fflush(stdout);
    exit(70);
}

static int sol_utf8_width(unsigned char first) {
    if (first < 0x80) return 1;
    if (first >= 0xC2 && first <= 0xDF) return 2;
    if (first >= 0xE0 && first <= 0xEF) return 3;
    if (first >= 0xF0 && first <= 0xF4) return 4;
    return 0;
}

static _Bool sol_utf8_sequence_valid(const unsigned char *data, int64_t remaining, int width) {
    if (width <= 0 || remaining < width) return 0;
    if (width == 1) return data[0] < 0x80;

    for (int index = 1; index < width; index++) {
        if (data[index] < 0x80 || data[index] > 0xBF) return 0;
    }

    if (width == 3) {
        if (data[0] == 0xE0 && data[1] < 0xA0) return 0;
        if (data[0] == 0xED && data[1] > 0x9F) return 0;
    }
    if (width == 4) {
        if (data[0] == 0xF0 && data[1] < 0x90) return 0;
        if (data[0] == 0xF4 && data[1] > 0x8F) return 0;
    }
    return 1;
}

static int32_t sol_utf8_decode(const unsigned char *data, int width) {
    if (width == 1) return data[0];
    if (width == 2) return (int32_t)(((data[0] & 0x1F) << 6) | (data[1] & 0x3F));
    if (width == 3) return (int32_t)(((data[0] & 0x0F) << 12) | ((data[1] & 0x3F) << 6) | (data[2] & 0x3F));
    return (int32_t)(((data[0] & 0x07) << 18) | ((data[1] & 0x3F) << 12) | ((data[2] & 0x3F) << 6) | (data[3] & 0x3F));
}

static int64_t sol_utf8_validate_and_count(const unsigned char *data, int64_t length) {
    int64_t offset = 0;
    int64_t count = 0;
    while (offset < length) {
        int width = sol_utf8_width(data[offset]);
        if (!sol_utf8_sequence_valid(data + offset, length - offset, width)) {
            sol_runtime_panic("Sol runtime error: text input is not valid UTF-8.");
        }
        offset += width;
        count++;
    }
    return count;
}

static int64_t sol_utf8_offset(SolString value, int64_t scalar, _Bool allow_end, const char *message) {
    if (scalar < 0 || scalar > value.scalar_length || (!allow_end && scalar == value.scalar_length)) {
        sol_runtime_panic(message);
    }
    int64_t offset = 0;
    int64_t index = 0;
    while (index < scalar) {
        int width = sol_utf8_width(value.data[offset]);
        if (!sol_utf8_sequence_valid(value.data + offset, value.byte_length - offset, width)) {
            sol_runtime_panic("Sol runtime error: text input is not valid UTF-8.");
        }
        offset += width;
        index++;
    }
    return offset;
}

int32_t sol_runtime_decode_literal_scalar(const unsigned char *data, int64_t length) {
    int width = length > 0 ? sol_utf8_width(data[0]) : 0;
    if (!sol_utf8_sequence_valid(data, length, width) || width != length) {
        sol_runtime_panic("Sol runtime error: invalid generated character literal.");
    }
    return sol_utf8_decode(data, width);
}

void sol_runtime_unknown_literal(void) {
    sol_runtime_panic("Sol runtime error: unknown generated literal identity.");
}

static SolString sol_string_concat(SolString left, SolString right) {
    if (left.byte_length < 0 || right.byte_length < 0 || left.byte_length > INT64_MAX - right.byte_length) {
        sol_runtime_panic("Sol runtime error: string concatenation length overflow.");
    }
    int64_t length = left.byte_length + right.byte_length;
    unsigned char *data = length == 0 ? NULL : malloc((size_t)length);
    if (length > 0 && data == NULL) {
        sol_runtime_panic("Sol runtime error: string concatenation allocation failed.");
    }
    if (left.byte_length > 0) memcpy(data, left.data, (size_t)left.byte_length);
    if (right.byte_length > 0) memcpy(data + left.byte_length, right.data, (size_t)right.byte_length);
    return (SolString){data, length, left.scalar_length + right.scalar_length};
}

void sol_runtime_string_concat(SolString *result, const unsigned char *left_data, int64_t left_bytes, int64_t left_scalars, const unsigned char *right_data, int64_t right_bytes, int64_t right_scalars) {
    *result = sol_string_concat(
        (SolString){left_data, left_bytes, left_scalars},
        (SolString){right_data, right_bytes, right_scalars}
    );
}

_Bool sol_runtime_string_equal(const unsigned char *left_data, int64_t left_bytes, const unsigned char *right_data, int64_t right_bytes) {
    SolString left = {left_data, left_bytes, 0};
    SolString right = {right_data, right_bytes, 0};
    return left.byte_length == right.byte_length
        && (left.byte_length == 0 || memcmp(left.data, right.data, (size_t)left.byte_length) == 0);
}

int32_t sol_runtime_string_index(const unsigned char *data, int64_t bytes, int64_t scalars, int64_t index) {
    SolString value = {data, bytes, scalars};
    int64_t offset = sol_utf8_offset(value, index, 0, "Sol runtime error: string index out of bounds.");
    int width = sol_utf8_width(value.data[offset]);
    return sol_utf8_decode(value.data + offset, width);
}

static SolString sol_string_slice(SolString value, int64_t start, int64_t end_index) {
    if (start < 0 || end_index < start || end_index > value.scalar_length) {
        sol_runtime_panic("Sol runtime error: invalid string slice range.");
    }
    int64_t start_byte = sol_utf8_offset(value, start, 1, "Sol runtime error: invalid string slice range.");
    int64_t end_byte = sol_utf8_offset(value, end_index, 1, "Sol runtime error: invalid string slice range.");
    return (SolString){value.data == NULL ? NULL : value.data + start_byte, end_byte - start_byte, end_index - start};
}

void sol_runtime_string_slice(SolString *result, const unsigned char *data, int64_t bytes, int64_t scalars, int64_t start, int64_t end_index) {
    *result = sol_string_slice((SolString){data, bytes, scalars}, start, end_index);
}

void sol_runtime_string_substring(SolString *result, const unsigned char *data, int64_t bytes, int64_t scalars, int64_t start, int64_t count) {
    SolString value = {data, bytes, scalars};
    if (count < 0 || start < 0 || start > value.scalar_length || count > value.scalar_length - start) {
        sol_runtime_panic("Sol runtime error: invalid string substring range.");
    }
    *result = sol_string_slice(value, start, start + count);
}

void sol_runtime_console_print(const unsigned char *data, int64_t bytes) {
    if (bytes > 0 && fwrite(data, 1, (size_t)bytes, stdout) != (size_t)bytes) {
        sol_runtime_panic("Sol runtime error: console output failed.");
    }
    fflush(stdout);
}

void sol_runtime_console_print_line(const unsigned char *data, int64_t bytes) {
    sol_runtime_console_print(data, bytes);
    if (fputc('\n', stdout) == EOF) sol_runtime_panic("Sol runtime error: console output failed.");
    fflush(stdout);
}

static SolString sol_read_stream(FILE *stream, _Bool line, const char *read_error) {
    size_t capacity = 256;
    size_t length = 0;
    unsigned char *data = malloc(capacity);
    if (data == NULL) sol_runtime_panic("Sol runtime error: text input allocation failed.");

    for (;;) {
        int next = fgetc(stream);
        if (next == EOF) {
            if (ferror(stream)) {
                free(data);
                sol_runtime_panic(read_error);
            }
            if (line && length == 0) {
                free(data);
                sol_runtime_panic("Sol runtime error: std.console.read_line reached EOF before a line was available.");
            }
            break;
        }
        if (line && next == '\n') break;
        if (length == capacity) {
            if (capacity > SIZE_MAX / 2) {
                free(data);
                sol_runtime_panic("Sol runtime error: text input is too large.");
            }
            capacity *= 2;
            unsigned char *resized = realloc(data, capacity);
            if (resized == NULL) {
                free(data);
                sol_runtime_panic("Sol runtime error: text input allocation failed.");
            }
            data = resized;
        }
        data[length++] = (unsigned char)next;
    }
    if (line && length > 0 && data[length - 1] == '\r') length--;
    int64_t scalars = sol_utf8_validate_and_count(data, (int64_t)length);
    if (length == 0) {
        free(data);
        data = NULL;
    }
    return (SolString){data, (int64_t)length, scalars};
}

void sol_runtime_console_read_line(SolString *result) {
    *result = sol_read_stream(stdin, 1, "Sol runtime error: std.console.read_line failed while reading input.");
}

static char *sol_path(SolString path) {
    if (path.byte_length < 0 || (uint64_t)path.byte_length > SIZE_MAX - 1) return NULL;
    char *result = malloc((size_t)path.byte_length + 1);
    if (result == NULL) return NULL;
    if (path.byte_length > 0) memcpy(result, path.data, (size_t)path.byte_length);
    if (memchr(result, 0, (size_t)path.byte_length) != NULL) {
        free(result);
        return NULL;
    }
    result[path.byte_length] = 0;
    return result;
}

_Bool sol_runtime_file_exists(const unsigned char *path_data, int64_t path_bytes) {
    SolString path = {path_data, path_bytes, 0};
    char *native = sol_path(path);
    if (native == NULL) return 0;
    FILE *file = fopen(native, "rb");
    free(native);
    if (file == NULL) return 0;
    fclose(file);
    return 1;
}

void sol_runtime_file_read_text(SolString *result, const unsigned char *path_data, int64_t path_bytes) {
    SolString path = {path_data, path_bytes, 0};
    char *native = sol_path(path);
    if (native == NULL) sol_runtime_panic("Sol runtime error: std.file.read_text could not open the file.");
    FILE *file = fopen(native, "rb");
    free(native);
    if (file == NULL) sol_runtime_panic("Sol runtime error: std.file.read_text could not open the file.");
    *result = sol_read_stream(file, 0, "Sol runtime error: std.file.read_text failed while reading the file.");
    if (fclose(file) != 0) sol_runtime_panic("Sol runtime error: std.file.read_text failed while closing the file.");
}

static _Bool sol_write_text(SolString path, SolString content, const char *mode) {
    char *native = sol_path(path);
    if (native == NULL) return 0;
    FILE *file = fopen(native, mode);
    free(native);
    if (file == NULL) return 0;
    _Bool written = content.byte_length == 0
        || fwrite(content.data, 1, (size_t)content.byte_length, file) == (size_t)content.byte_length;
    return fclose(file) == 0 && written;
}

_Bool sol_runtime_file_write_text(const unsigned char *path_data, int64_t path_bytes, const unsigned char *content_data, int64_t content_bytes) {
    return sol_write_text((SolString){path_data, path_bytes, 0}, (SolString){content_data, content_bytes, 0}, "wb");
}

_Bool sol_runtime_file_append_text(const unsigned char *path_data, int64_t path_bytes, const unsigned char *content_data, int64_t content_bytes) {
    return sol_write_text((SolString){path_data, path_bytes, 0}, (SolString){content_data, content_bytes, 0}, "ab");
}

void sol_runtime_vector_fail_allocation(void) {
    sol_runtime_panic("Sol runtime error: vector allocation failed.");
}

void sol_runtime_vector_fail_bounds(void) {
    sol_runtime_panic("Sol runtime error: vector index out of bounds.");
}

void sol_runtime_vector_fail_capacity(void) {
    sol_runtime_panic("Sol runtime error: invalid or overflowing vector capacity.");
}

void sol_runtime_vector_fail_empty_pop(void) {
    sol_runtime_panic("Sol runtime error: cannot pop an empty vector.");
}
