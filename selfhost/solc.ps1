$ErrorActionPreference = "Stop"
$CliArguments = [string[]] $args
$SelfhostDirectory = $PSScriptRoot
$Core = if ($env:SOL_SELFHOST_CORE) { $env:SOL_SELFHOST_CORE } else { Join-Path $SelfhostDirectory "build\stage1\solc-core.exe" }
$StandardLibrary = if ($env:SOL_SELFHOST_STDLIB) { $env:SOL_SELFHOST_STDLIB } else { Join-Path $SelfhostDirectory "stdlib" }
$NativeLink = if ($env:SOL_SELFHOST_NATIVE_LINK) { $env:SOL_SELFHOST_NATIVE_LINK } else { Join-Path $SelfhostDirectory "native-link.bat" }

function Exit-CommandError([string] $Message) {
    [Console]::Error.WriteLine("command-line error: $Message")
    exit 2
}

if ($CliArguments.Count -eq 1 -and ($CliArguments[0] -eq "--version" -or $CliArguments[0] -eq "-v")) {
    [Console]::Out.WriteLine("Sol 0.1.1")
    exit 0
}

$Source = $null
$Output = $null
$Keep = $false
$PositionalOnly = $false
for ($Index = 0; $Index -lt $CliArguments.Count; $Index++) {
    $Argument = $CliArguments[$Index]
    if (-not $PositionalOnly -and $Argument -eq "--") {
        $PositionalOnly = $true
        continue
    }
    if (-not $PositionalOnly) {
        if ($Argument -eq "--keep-intermediates") {
            $Keep = $true
            continue
        }
        if ($Argument -eq "-o" -or $Argument -eq "--output") {
            if ($null -ne $Output) { Exit-CommandError "Compiler output path may only be specified once." }
            if ($Index + 1 -ge $CliArguments.Count) { Exit-CommandError "Option '$Argument' requires an output path." }
            $Index++
            $Output = $CliArguments[$Index]
            if ([string]::IsNullOrWhiteSpace($Output)) { Exit-CommandError "Compiler output path must not be blank." }
            continue
        }
        if ($Argument.StartsWith("--output=")) {
            if ($null -ne $Output) { Exit-CommandError "Compiler output path may only be specified once." }
            $Output = $Argument.Substring("--output=".Length)
            if ([string]::IsNullOrWhiteSpace($Output)) { Exit-CommandError "Compiler output path must not be blank." }
            continue
        }
        if ($Argument.StartsWith("-")) { Exit-CommandError "Unknown compiler option '$Argument'." }
    }
    if ($null -ne $Source) { Exit-CommandError "Compiler expects exactly one source file, but received both '$Source' and '$Argument'." }
    $Source = $Argument
}

if ([string]::IsNullOrWhiteSpace($Source)) { Exit-CommandError "Compiler requires one Sol source file." }
if ($Source -match "[\r\n]" -or ($null -ne $Output -and $Output -match "[\r\n]")) {
    Exit-CommandError "Bootstrap CLI paths must not contain newlines."
}

try {
    $SourcePath = [IO.Path]::GetFullPath($Source)
} catch {
    Exit-CommandError "Compiler source path '$Source' is invalid."
}
$SourceName = [IO.Path]::GetFileName($SourcePath)
if (-not $SourceName.EndsWith(".sol", [StringComparison]::OrdinalIgnoreCase)) {
    [Console]::Error.WriteLine("input error: Sol source file '$Source' must use the '.sol' extension.")
    exit 3
}
$ModuleName = $SourceName.Substring(0, $SourceName.Length - 4)
if ([string]::IsNullOrWhiteSpace($ModuleName)) {
    [Console]::Error.WriteLine("input error: Sol source file '$Source' must have a name before '.sol'.")
    exit 3
}
$ModuleRoot = [IO.Path]::GetDirectoryName($SourcePath)
if (-not [IO.File]::Exists($SourcePath)) {
    [Console]::Error.WriteLine("input error: Sol source file '$Source' does not exist or is not a regular file.")
    exit 3
}

if ($null -eq $Output) {
    $Output = Join-Path $ModuleRoot $ModuleName
} else {
    try {
        $Output = [IO.Path]::GetFullPath($Output)
    } catch {
        Exit-CommandError "Compiler output path '$Output' is invalid."
    }
}
if (-not $Output.EndsWith(".exe", [StringComparison]::OrdinalIgnoreCase)) { $Output += ".exe" }

if (-not [IO.File]::Exists($Core)) {
    [Console]::Error.WriteLine("toolchain error: self-host compiler core is not executable: $Core")
    exit 7
}
if (-not [IO.File]::Exists($NativeLink)) {
    [Console]::Error.WriteLine("toolchain error: native link driver is not executable: $NativeLink")
    exit 7
}
if (-not [IO.Directory]::Exists($StandardLibrary)) {
    [Console]::Error.WriteLine("input error: bundled standard library was not found: $StandardLibrary")
    exit 3
}

$Request = [IO.Path]::GetTempFileName()
$LlvmOutput = "$Output.sol-selfhost.ll"
$LiteralOutput = "$Output.sol-selfhost-literals.c"
try {
    [IO.File]::WriteAllLines($Request, [string[]] @(
        "SOL-SELFHOST-REQUEST-1",
        $SourcePath,
        $ModuleRoot,
        $ModuleName,
        [IO.Path]::GetFullPath($StandardLibrary),
        $LlvmOutput,
        $LiteralOutput
    ), [Text.UTF8Encoding]::new($false))
    Remove-Item -LiteralPath $LlvmOutput, $LiteralOutput -Force -ErrorAction SilentlyContinue

    $ProcessInfo = [Diagnostics.ProcessStartInfo]::new()
    $ProcessInfo.FileName = $Core
    $ProcessInfo.UseShellExecute = $false
    $ProcessInfo.RedirectStandardInput = $true
    $ProcessInfo.RedirectStandardOutput = $true
    $ProcessInfo.RedirectStandardError = $true
    $Utf8NoBom = [Text.UTF8Encoding]::new($false)
    $ProcessInfo.StandardInputEncoding = $Utf8NoBom
    $ProcessInfo.StandardOutputEncoding = $Utf8NoBom
    $ProcessInfo.StandardErrorEncoding = $Utf8NoBom
    $Process = [Diagnostics.Process]::new()
    $Process.StartInfo = $ProcessInfo
    if (-not $Process.Start()) { throw "self-host compiler core did not start" }
    $StandardOutput = $Process.StandardOutput.ReadToEndAsync()
    $StandardError = $Process.StandardError.ReadToEndAsync()
    $Process.StandardInput.WriteLine($Request)
    $Process.StandardInput.Close()
    $Process.WaitForExit()
    [Console]::Error.Write($StandardOutput.Result)
    [Console]::Error.Write($StandardError.Result)
    $CoreStatus = $Process.ExitCode
    $Process.Dispose()
    if ($CoreStatus -ne 0) {
        Remove-Item -LiteralPath $LlvmOutput, $LiteralOutput -Force -ErrorAction SilentlyContinue
        exit $CoreStatus
    }

    $PreviousKeep = $env:SOL_KEEP_INTERMEDIATES
    if ($Keep) { $env:SOL_KEEP_INTERMEDIATES = "1" } else { $env:SOL_KEEP_INTERMEDIATES = "0" }
    & $NativeLink $LlvmOutput $LiteralOutput $Output | Out-Null
    $LinkStatus = $LASTEXITCODE
    if ($null -eq $PreviousKeep) { Remove-Item Env:SOL_KEEP_INTERMEDIATES -ErrorAction SilentlyContinue } else { $env:SOL_KEEP_INTERMEDIATES = $PreviousKeep }
    if ($LinkStatus -ne 0) {
        Remove-Item -LiteralPath $LlvmOutput, $LiteralOutput -Force -ErrorAction SilentlyContinue
        [Console]::Error.WriteLine("toolchain error: native compiler driver failed with exit code $LinkStatus.")
        exit 7
    }
    if (-not $Keep) { Remove-Item -LiteralPath $LlvmOutput, $LiteralOutput -Force -ErrorAction SilentlyContinue }
    exit 0
} finally {
    Remove-Item -LiteralPath $Request -Force -ErrorAction SilentlyContinue
}
