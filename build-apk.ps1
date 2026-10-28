param(
    [string]$SdkRoot = "$PSScriptRoot\.android-sdk",
    [string]$JdkHome = "$env:JAVA_HOME"
)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$buildDir = Join-Path $projectRoot ".build"
$distDir = Join-Path $projectRoot "dist"

if (-not $JdkHome -or -not (Test-Path (Join-Path $JdkHome "bin\javac.exe"))) {
    throw "JDK not found. Set JAVA_HOME to a JDK 17+ installation."
}
if (-not (Test-Path $SdkRoot)) {
    throw "Android SDK not found: $SdkRoot. Install it or pass -SdkRoot."
}

$buildTools = Get-ChildItem (Join-Path $SdkRoot "build-tools") -Directory -ErrorAction SilentlyContinue |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$platforms = Get-ChildItem (Join-Path $SdkRoot "platforms") -Directory -ErrorAction SilentlyContinue |
    Sort-Object { [int]($_.Name -replace 'android-', '') } -Descending | Select-Object -First 1
if (-not $buildTools -or -not $platforms) {
    throw "SDK is missing build-tools or platforms. Install build-tools;35.0.0 and platforms;android-35."
}

$env:JAVA_HOME = $JdkHome
Push-Location $projectRoot
try {
    # aapt2 on Windows struggles with non-ASCII absolute paths, so every native
    # tool is invoked with paths relative to the project root.
    $sdkRel = Resolve-Path -Relative $SdkRoot
    $androidJar = Join-Path $sdkRel ("platforms\" + $platforms.Name + "\android.jar")
    $aapt2 = Join-Path $sdkRel ("build-tools\" + $buildTools.Name + "\aapt2.exe")
    $zipalign = Join-Path $sdkRel ("build-tools\" + $buildTools.Name + "\zipalign.exe")
    $apksigner = Join-Path $sdkRel ("build-tools\" + $buildTools.Name + "\apksigner.bat")
    $d8 = Join-Path $sdkRel ("build-tools\" + $buildTools.Name + "\d8.bat")
    $javac = Join-Path $JdkHome "bin\javac.exe"
    $keytool = Join-Path $JdkHome "bin\keytool.exe"
    $jar = Join-Path $JdkHome "bin\jar.exe"

    Write-Host "==> JDK: $JdkHome"
    Write-Host "==> Build tools: $($buildTools.Name)"
    Write-Host "==> Platform: $($platforms.Name)"

    if (Test-Path $buildDir) { Remove-Item $buildDir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $buildDir, ".build\gen", ".build\classes", ".build\dex", $distDir, "signing" | Out-Null

    $resZip = ".build\resources.zip"
    $unsigned = ".build\app-unsigned.apk"
    $aligned = ".build\app-aligned.apk"
    $output = Join-Path $distDir "FloatingClock-v1.0.apk"
    $keystore = "signing\floatingclock.keystore"
    $storePass = "floatingclock"

    Write-Host "==> Compiling resources"
    & $aapt2 compile --dir "app\src\main\res" -o $resZip
    if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

    Write-Host "==> Linking resources and manifest"
    & $aapt2 link -o $unsigned -I $androidJar --manifest "app\src\main\AndroidManifest.xml" --java ".build\gen" --min-sdk-version 26 --target-sdk-version 35 --version-code 1 --version-name "1.0" --auto-add-overlay $resZip
    if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

    Write-Host "==> Compiling Java"
    $prefixLength = $projectRoot.Length + 1
    $sources = @()
    $sources += Get-ChildItem "app\src\main\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName.Substring($prefixLength) }
    $sources += Get-ChildItem ".build\gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName.Substring($prefixLength) }
    $sourceList = ".build\sources.txt"
    $sources | Set-Content -Path $sourceList -Encoding ASCII
    & $javac -encoding UTF-8 -source 17 -target 17 -Xlint:-options -classpath $androidJar -d ".build\classes" "@$sourceList"
    if ($LASTEXITCODE -ne 0) { throw "javac failed" }

    Write-Host "==> Dexing"
    $classes = Get-ChildItem ".build\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName.Substring($prefixLength) }
    & $d8 --lib $androidJar --min-api 26 --output ".build\dex" $classes
    if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

    Write-Host "==> Packaging"
    & $jar uf $unsigned -C ".build\dex" classes.dex
    if ($LASTEXITCODE -ne 0) { throw "packaging classes.dex failed" }

    Write-Host "==> Aligning"
    & $zipalign -f 4 $unsigned $aligned
    if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

    if (-not (Test-Path $keystore)) {
        Write-Host "==> Creating local signing key"
        & $keytool -genkeypair -v -keystore $keystore -storepass $storePass -keypass $storePass -alias floatingclock -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Floating Clock, OU=Personal, O=Personal, L=Local, ST=Local, C=CN"
        if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
    }

    Write-Host "==> Signing"
    & $apksigner sign --ks $keystore --ks-pass "pass:$storePass" --key-pass "pass:$storePass" --out $output $aligned
    if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

    Write-Host "==> Verifying"
    & $apksigner verify --verbose --print-certs $output
    if ($LASTEXITCODE -ne 0) { throw "signature verification failed" }

    $hash = (Get-FileHash $output -Algorithm SHA256).Hash
    Set-Content -Path (Join-Path $distDir "SHA256.txt") -Value "$hash  FloatingClock-v1.0.apk" -Encoding ASCII
    Write-Host ""
    Write-Host "APK: $output"
    Write-Host "SHA256: $hash"
}
finally {
    Pop-Location
}

exit 0
