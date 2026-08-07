package io.github.stardragonstudios.sol.toolchain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class NativeProcessFixture {
    private NativeProcessFixture() {}

    public static void main(String[] arguments) throws IOException {
        switch (arguments[0]) {
            case "simple" -> simple(arguments);
            case "bulk" -> bulk(arguments);

            default -> throw new IllegalArgumentException("Unknown fixture mode '%s'.".formatted(arguments[0]));
        }
    }

    private static void simple(String[] arguments) {
        System.out.print(arguments[2]);
        System.err.print(arguments[3]);
        System.out.flush();
        System.err.flush();

        System.exit(Integer.parseInt(arguments[1]));
    }

    private static void bulk(String[] arguments) throws IOException {
        var size = Integer.parseInt(arguments[1]);
        var standardOutput = new byte[size];
        var standardError = new byte[size];

        Arrays.fill(standardOutput, (byte) 'o');
        Arrays.fill(standardError, (byte) 'e');

        System.out.write(new String(standardOutput, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
        System.err.write(new String(standardError, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));

        System.out.flush();
        System.err.flush();
    }
}
