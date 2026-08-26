$ErrorActionPreference = "Stop"
$CliArguments = [string[]] $args
$SelfhostDirectory = $PSScriptRoot
$Solc = if ($env:SOL_SELFHOST_SOLC) { $env:SOL_SELFHOST_SOLC } else { Join-Path $SelfhostDirectory "solc.bat" }

function Exit-CommandError([string] $Message) {
    [Console]::Error.WriteLine("command-line error: $Message")
    exit 2
}

if ($CliArguments.Count -eq 1 -and ($CliArguments[0] -eq "--version" -or $CliArguments[0] -eq "-v")) {
    [Console]::Out.WriteLine("Sol 0.1.1")
    exit 0
}
if ($CliArguments.Count -eq 0) { Exit-CommandError "Sol requires a command." }
$Command = $CliArguments[0]
if ($Command -ne "run") { Exit-CommandError "Unknown Sol command '$Command'." }

$Source = $null
$PositionalOnly = $false
for ($Index = 1; $Index -lt $CliArguments.Count; $Index++) {
    $Argument = $CliArguments[$Index]
    if (-not $PositionalOnly -and $Argument -eq "--") {
        $PositionalOnly = $true
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
    & $Solc -o $RunOutput -- $Source
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
