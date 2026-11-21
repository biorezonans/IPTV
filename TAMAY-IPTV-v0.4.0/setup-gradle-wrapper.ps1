$ErrorActionPreference = 'Stop'
$gradleVersion = '8.13'
$zipPath = Join-Path $env:TEMP "gradle-$gradleVersion-bin.zip"
$extractRoot = Join-Path $env:TEMP "tamay-gradle-$gradleVersion"
$gradleHome = Join-Path $extractRoot "gradle-$gradleVersion"
$url = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"

if (-not (Test-Path (Join-Path $gradleHome 'bin/gradle.bat'))) {
    Write-Host "Gradle $gradleVersion indiriliyor..."
    Invoke-WebRequest -Uri $url -OutFile $zipPath
    New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null
    Expand-Archive -Path $zipPath -DestinationPath $extractRoot -Force
}

Write-Host 'Gradle Wrapper oluşturuluyor...'
Push-Location $PSScriptRoot
try {
    & (Join-Path $gradleHome 'bin/gradle.bat') wrapper --gradle-version $gradleVersion
    if ($LASTEXITCODE -ne 0) { throw 'Gradle Wrapper oluşturulamadı.' }
}
finally {
    Pop-Location
}
Write-Host 'Tamamlandı. Bundan sonra Android Studio projeyi gradlew üzerinden açabilir.'
