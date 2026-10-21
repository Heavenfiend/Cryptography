param(
    [switch]$Test,
    [int]$Bits = 128,
    [double]$Probability = 0.999999999999,
    [ValidateSet('FERMAT', 'SOLOVAY_STRASSEN', 'MILLER_RABIN')]
    [string]$TestType = 'MILLER_RABIN',
    [string]$Message = 'Вторая лабораторная по криптографии. Kotlin и RSA.',
    [string]$Compiler = '',
    [string]$Java = 'java'
)

$ErrorActionPreference = 'Stop'
if (-not $Compiler) {
    $compilerCommand = Get-Command kotlinc -ErrorAction SilentlyContinue
    if ($compilerCommand) {
        $Compiler = $compilerCommand.Source
    } else {
        $candidates = @(Get-ChildItem -Path "$env:ProgramFiles\JetBrains\IntelliJ IDEA *\plugins\Kotlin\kotlinc\bin\kotlinc.bat" -ErrorAction SilentlyContinue | Sort-Object FullName -Descending)
        if ($candidates.Count -eq 0) { throw 'Компилятор Kotlin не найден. Передай путь через -Compiler.' }
        $Compiler = $candidates[0].FullName
    }
}

$outputDirectory = Join-Path $PSScriptRoot 'out'
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Filter '*.kt' | Select-Object -ExpandProperty FullName)
$mainClass = 'MainKt'
$jar = Join-Path $outputDirectory 'lab2.jar'
if ($Test) {
    $sources += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'test') -Filter '*.kt' | Select-Object -ExpandProperty FullName)
    $mainClass = 'Lab2Checks'
    $jar = Join-Path $outputDirectory 'checks.jar'
}
& $Compiler @sources -jvm-target 17 -include-runtime -d $jar
if ($LASTEXITCODE -ne 0) { throw 'Не удалось собрать лабораторную' }
if ($Test) {
    & $Java '-Dfile.encoding=UTF-8' -cp $jar $mainClass
} else {
    $probabilityText = $Probability.ToString('R', [Globalization.CultureInfo]::InvariantCulture)
    & $Java '-Dfile.encoding=UTF-8' -cp $jar $mainClass $Bits $probabilityText $TestType $Message
}
if ($LASTEXITCODE -ne 0) { throw 'Программа завершилась с ошибкой' }
