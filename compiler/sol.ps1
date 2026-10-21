$ErrorActionPreference = "Stop"
$CliArguments = [string[]] $args
$SelfhostDirectory = $PSScriptRoot
$Solc = if ($env:SOL_SELFHOST_SOLC) { $env:SOL_SELFHOST_SOLC } else { Join-Path $SelfhostDirectory "solc.bat" }

function Exit-CommandError([string] $Message) {
    [Console]::Error.WriteLine("command-line error: $Message")
    exit 2
}

if ($CliArguments.Count -eq 1 -and ($CliArguments[0] -eq "--version" -or $CliArguments[0] -eq "-v")) {
    [Console]::Out.WriteLine("Sol 0.2.0")
    exit 0
}
if ($CliArguments.Count -eq 0) { Exit-CommandError "Sol requires a command." }
$Command = $CliArguments[0]
if ($Command -ne "run") { Exit-CommandError "Unknown Sol command '$Command'." }

$Source = $null
$PositionalOnly = $false
$LanguageMode = "legacy"
$ModeSeen = $false
for ($Index = 1; $Index -lt $CliArguments.Count; $Index++) {
    $Argument = $CliArguments[$Index]
    if (-not $PositionalOnly -and $Argument -eq "--") {
        $PositionalOnly = $true
        continue
    }
    if (-not $PositionalOnly -and ($Argument -ceq "--language-mode" -or $Argument.StartsWith("--language-mode="))) {
        if ($null -ne $Source) { Exit-CommandError "Language mode must precede the run source." }
        if ($ModeSeen) { Exit-CommandError "Language mode may only be specified once." }
        $ModeSeen = $true
        if ($Argument -ceq "--language-mode") {
            if ($Index + 1 -ge $CliArguments.Count) { Exit-CommandError "Option '--language-mode' requires a value." }
            $Index++
            $LanguageMode = $CliArguments[$Index]
        } else { $LanguageMode = $Argument.Substring("--language-mode=".Length) }
        if ($LanguageMode -cne "legacy" -and $LanguageMode -cne "safe-experimental") { Exit-CommandError "Unknown language mode '$LanguageMode'." }
        continue
    }
    if (-not $PositionalOnly -and $Argument.StartsWith("-")) { Exit-CommandError "Unknown run option '$Argument'." }
    if ($null -ne $Source) { Exit-CommandError "Run expects exactly one source file, but received both '$Source' and '$Argument'." }
    $Source = $Argument
}
if ([string]::IsNullOrWhiteSpace($Source)) { Exit-CommandError "Run requires one Sol source file." }

$RunDirectory = Join-Path ([IO.Path]::GetTempPath()) ("sol-run-" + [Guid]::NewGuid().ToString("N"))
[IO.Directory]::CreateDirectory($RunDirectory) | Out-Null
$RunOutput = Join-Path $RunDirectory "program"
try {
    if ($ModeSeen) { & $Solc "--language-mode=$LanguageMode" -o $RunOutput -- $Source }
    else { & $Solc -o $RunOutput -- $Source }
    $CompileStatus = $LASTEXITCODE
    if ($CompileStatus -ne 0) { exit $CompileStatus }
    $Executable = if ([IO.File]::Exists("$RunOutput.exe")) { "$RunOutput.exe" } else { $RunOutput }
    if (-not [IO.File]::Exists($Executable)) {
        [Console]::Error.WriteLine("execution error: compiled program is not executable: $Executable")
        exit 8
    }
    & $Executable
    exit $LASTEXITCODE
} finally {
    Remove-Item -LiteralPath $RunDirectory -Recurse -Force -ErrorAction SilentlyContinue
}
