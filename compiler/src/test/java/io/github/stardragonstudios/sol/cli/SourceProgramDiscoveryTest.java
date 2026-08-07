package io.github.stardragonstudios.sol.cli;

import io.github.stardragonstudios.sol.semantics.ModuleName;

import io.github.stardragonstudios.sol.semantics.SourceModule;
import io.github.stardragonstudios.sol.syntax.FunctionDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceProgramDiscoveryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversDirectInjectedModule()
        throws IOException {

        write(
            "main.sol",
            """
            inject helper

            @init
            fn launch() -> int
                return helperValue()
            end
            """
        );

        write(
            "helper.sol",
            """
            fn helperValue() -> int
                return 42
            end
            """
        );

        var program = SourceProgramDiscovery.discover(temporaryDirectory.resolve("main.sol"));

        assertEquals(
            List.of(new ModuleName(List.of("main")), new ModuleName(List.of("helper"))),
            program.modules().stream().map(SourceModule::name).toList()
        );
    }

    @Test
    void mapsQualifiedModuleNameToDirectories()
        throws IOException {

        write(
            "main.sol",
            """
            inject utilities.math

            @init
            fn launch() -> int
                return addOne(41)
            end
            """
        );

        var directory = temporaryDirectory.resolve("utilities");

        Files.createDirectories(directory);

        Files.writeString(
            directory.resolve(
                "math.sol"
            ),
            """
            fn addOne(value: int) -> int
                return value + 1
            end
            """
        );

        var program = SourceProgramDiscovery.discover(temporaryDirectory.resolve("main.sol"));

        assertTrue(program.modules().stream().anyMatch(module -> module.name().equals(new ModuleName(List.of("utilities", "math")))));
    }

    @Test
    void discoversTransitiveInjectedModules()
        throws IOException {

        write(
            "main.sol",
            """
            inject first

            @init
            fn launch() -> int
                return firstValue()
            end
            """
        );

        write(
            "first.sol",
            """
            inject second

            fn firstValue() -> int
                return secondValue()
            end
            """
        );

        write(
            "second.sol",
            """
            fn secondValue() -> int
                return 42
            end
            """
        );

        var program = SourceProgramDiscovery.discover(temporaryDirectory.resolve("main.sol"));

        assertEquals(3, program.modules().size());
    }

    @Test
    void cyclicInjectionsTerminate()
        throws IOException {

        write(
            "main.sol",
            """
            inject helper

            @init
            fn launch() -> int
                return value()
            end
            """
        );

        write(
            "helper.sol",
            """
            inject main

            fn value() -> int
                return 42
            end
            """
        );

        var program = SourceProgramDiscovery.discover(temporaryDirectory.resolve("main.sol"));

        assertEquals(2, program.modules().size());
    }

    @Test
    void missingInjectedModuleIsLeftForSemanticAnalysis()
        throws IOException {

        write(
            "main.sol",
            """
            inject missing

            @init
            fn launch() -> int
                return 0
            end
            """
        );

        var program = SourceProgramDiscovery.discover(temporaryDirectory.resolve("main.sol"));

        assertEquals(1, program.modules().size());
    }

    @Test
    void syntaxErrorInInjectedModuleUsesInjectedSourcePath()
        throws IOException {

        write(
            "main.sol",
            """
            inject broken

            @init
            fn launch() -> int
                return 0
            end
            """
        );

        var broken = write("broken.sol", "$");

        var exception = assertThrows(
            FrontendCompilationException.class,
            () -> SourceProgramDiscovery.discover(temporaryDirectory.resolve("main.sol"))
        );

        assertEquals(broken.toAbsolutePath().normalize(), exception.diagnostics().getFirst().sourceFile());
    }

    @Test
    void discoversBundledConsoleModule()
        throws IOException {

        write(
            "main.sol",
            """
            inject std.console only print_line

            @init
            fn launch() -> int
                print_line("Hello")
                return 0
            end
            """
        );

        var program = SourceProgramDiscovery.discover(temporaryDirectory.resolve("main.sol"));

        assertEquals(
            List.of(new ModuleName(List.of("main")), new ModuleName(List.of("std", "console"))),
            program.modules().stream().map(module -> module.name()).toList()
        );

        assertTrue(program.sourceFileOf(new ModuleName(List.of("std", "console"))).endsWith(Path.of(".sol-stdlib", "std", "console.sol")));
    }

    @Test
    void bundledConsoleModuleTakesPrecedenceOverProjectFile()
        throws IOException {

        write(
            "main.sol",
            """
            inject std.console only print_line

            @init
            fn launch() -> int
                print_line("Hello")
                return 0
            end
            """
        );

        var standardDirectory = temporaryDirectory.resolve("std");

        Files.createDirectories(standardDirectory);

        Files.writeString(
            standardDirectory.resolve("console.sol"),
            """
            fn fake() -> int
                return 42
            end
            """
        );

        var program = SourceProgramDiscovery.discover(temporaryDirectory.resolve("main.sol"));

        var console = program.modules()
            .stream()
            .filter(module -> module.name().equals(new ModuleName(List.of("std", "console"))))
            .findFirst()
            .orElseThrow();

        assertTrue(
            console.unit()
                .declarations()
                .stream()
                .anyMatch(declaration -> declaration instanceof FunctionDeclaration function && function.name().equals("print_line"))
        );
    }

    @Test
    void discoversBundledFileModule()
        throws IOException {

        write(
            "main.sol",
            """
            inject namespace std.file as file

            @init
            fn launch() -> int
                return 0
            end
            """
        );

        var program = SourceProgramDiscovery.discover(temporaryDirectory.resolve("main.sol"));

        var fileModule = program.modules()
            .stream()
            .filter(module -> module.name().equals(new ModuleName(List.of("std", "file"))))
            .findFirst()
            .orElseThrow();

        var functionNames = fileModule.unit()
            .declarations()
            .stream()
            .map(FunctionDeclaration.class::cast)
            .map(FunctionDeclaration::name)
            .toList();

        assertEquals(
            List.of(
                "exists",
                "write_text",
                "append_text"
            ),
            functionNames
        );
    }

    @Test
    void bundledFileModuleTakesPrecedenceOverProjectFile()
        throws IOException {

        write(
            "main.sol",
            """
            inject namespace std.file as file

            @init
            fn launch() -> int
                if file::exists("anything.txt") then
                    return 1
                else
                    return 0
                end
            end
            """
        );

        var standardDirectory = temporaryDirectory.resolve("std");

        Files.createDirectories(standardDirectory);

        Files.writeString(
            standardDirectory.resolve("file.sol"),
            """
            fn fake() -> int
                return 42
            end
            """
        );

        var program = SourceProgramDiscovery.discover(temporaryDirectory.resolve("main.sol"));

        var file = program.modules()
            .stream()
            .filter(module -> module.name().equals(new ModuleName(List.of("std", "file"))))
            .findFirst()
            .orElseThrow();

        var functionNames = file.unit()
            .declarations()
            .stream()
            .filter(FunctionDeclaration.class::isInstance)
            .map(FunctionDeclaration.class::cast)
            .map(FunctionDeclaration::name)
            .toList();

        assertEquals(List.of("exists", "write_text", "append_text"), functionNames);
    }

    private Path write(String name, String source) throws IOException {
        var path = temporaryDirectory.resolve(name);

        Files.writeString(path, source);

        return path;
    }
}
