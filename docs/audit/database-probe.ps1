$ErrorActionPreference = 'Stop'
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..'))
try {
    # mvn test creates the classpath manifest. No extra dependencies are downloaded.
    $auditReport = Get-ChildItem -LiteralPath 'target/surefire-reports' -Filter 'TEST-*.xml' | Select-Object -First 1
    if (-not $auditReport) { throw 'Run mvn test before this probe.' }
    [xml]$auditXml = Get-Content -LiteralPath $auditReport.FullName -Raw
    $auditClasspath = ($auditXml.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
    if (-not $auditClasspath) { throw 'Classpath not found in Surefire report.' }
    $auditArguments = '"-cp"' + "`n" + '"' + $auditClasspath.Replace('\', '/') + '"' + "`n" + '"docs/audit/DatabaseAudit.java"'
    [System.IO.File]::WriteAllText((Join-Path (Get-Location) 'target/audit-database.args'), $auditArguments)
    & java '@target/audit-database.args'
    if ($LASTEXITCODE -ne 0) { throw "Database audit exited with code $LASTEXITCODE" }
} finally { Pop-Location }
