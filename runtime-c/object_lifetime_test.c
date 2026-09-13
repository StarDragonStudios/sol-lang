/* Include the production implementation with a test-only allocator interposer.
 * No environment switches or failure-injection hooks enter the shipped runtime. */
/* Exercise the portable C API without Microsoft's fopen_s migration warning. */
#if defined(_WIN32) && !defined(_CRT_SECURE_NO_WARNINGS)
#define _CRT_SECURE_NO_WARNINGS
#endif
#include <assert.h>
#include <stddef.h>
#include <stdlib.h>

static int fail_allocation;
static int allocations;
static int releases;
static void *test_malloc(size_t bytes) {
    allocations++;
    return fail_allocation ? NULL : malloc(bytes);
}
static void test_free(void *object) {
    releases++;
    free(object);
}

#define malloc test_malloc
#define free test_free
#define sol_runtime_object_allocate tested_object_allocate
#include "selfhost.c"
#undef sol_runtime_object_allocate
#undef malloc
#undef free

void *sol_runtime_object_allocate(int64_t bytes) {
    fail_allocation = 1;
    void *result = tested_object_allocate(bytes);
    fail_allocation = 0;
    return result;
}

#ifndef SOL_OBJECT_FAILURE_FIXTURE
int main(void) {
    assert(tested_object_allocate(0) == NULL);
    assert(tested_object_allocate(-1) == NULL);
    assert(allocations == 0);
    void *object = tested_object_allocate(64);
    assert(object != NULL);
    assert((uintptr_t)object % _Alignof(max_align_t) == 0);
    memset(object, 0x5a, 64);
    sol_runtime_object_delete(object);
    sol_runtime_object_delete(NULL);
    assert(releases == 2);
    assert(sol_runtime_object_allocate(64) == NULL);
    assert(allocations == 2);
    return 0;
}
#endif
