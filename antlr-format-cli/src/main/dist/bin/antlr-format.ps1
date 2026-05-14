$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AppHome = Split-Path -Parent $ScriptDir
$JarPath = Join-Path $AppHome "lib/${project.build.finalName}.jar"

if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin/java.exe"))) {
    $JavaBin = Join-Path $env:JAVA_HOME "bin/java.exe"
} else {
    $JavaBin = "java"
}

$javaArgs = @()
if ($env:ANTLR_FORMAT_JAVA_OPTS) {
    $javaArgs += [System.Management.Automation.PSParser]::Tokenize($env:ANTLR_FORMAT_JAVA_OPTS, [ref]$null) |
        Where-Object { $_.Type -eq 'CommandArgument' } |
        ForEach-Object { $_.Content }
}
$javaArgs += '-jar'
$javaArgs += $JarPath
$javaArgs += $args

& $JavaBin @javaArgs
exit $LASTEXITCODE

