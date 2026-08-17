#ifndef SOL_SELFHOST_RUNTIME_H
#define SOL_SELFHOST_RUNTIME_H

#include <stdint.h>

typedef struct SolString {
    const unsigned char *data;
    int64_t byte_length;
    int64_t scalar_length;
} SolString;

int32_t sol_runtime_char_literal(int64_t function_id, int64_t value_id);
void sol_runtime_string_literal(SolString *result, int64_t function_id, int64_t value_id);

void sol_runtime_string_concat(SolString *result, const unsigned char *left_data, int64_t left_bytes, int64_t left_scalars, const unsigned char *right_data, int64_t right_bytes, int64_t right_scalars);
_Bool sol_runtime_string_equal(const unsigned char *left_data, int64_t left_bytes, const unsigned char *right_data, int64_t right_bytes);
int32_t sol_runtime_string_index(const unsigned char *data, int64_t bytes, int64_t scalars, int64_t index);
void sol_runtime_string_slice(SolString *result, const unsigned char *data, int64_t bytes, int64_t scalars, int64_t start, int64_t end_index);
void sol_runtime_string_substring(SolString *result, const unsigned char *data, int64_t bytes, int64_t scalars, int64_t start, int64_t count);

void sol_runtime_console_print(const unsigned char *data, int64_t bytes);
void sol_runtime_console_print_line(const unsigned char *data, int64_t bytes);
void sol_runtime_console_read_line(SolString *result);

_Bool sol_runtime_file_exists(const unsigned char *path_data, int64_t path_bytes);
void sol_runtime_file_read_text(SolString *result, const unsigned char *path_data, int64_t path_bytes);
_Bool sol_runtime_file_write_text(const unsigned char *path_data, int64_t path_bytes, const unsigned char *content_data, int64_t content_bytes);
_Bool sol_runtime_file_append_text(const unsigned char *path_data, int64_t path_bytes, const unsigned char *content_data, int64_t content_bytes);

void sol_runtime_vector_fail_allocation(void);
void sol_runtime_vector_fail_bounds(void);
void sol_runtime_vector_fail_capacity(void);
void sol_runtime_vector_fail_empty_pop(void);

int32_t sol_runtime_decode_literal_scalar(const unsigned char *data, int64_t length);
void sol_runtime_unknown_literal(void);

#endif
