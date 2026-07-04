param(
    [string]$Type = "small" # Can be "small", "minor", or "major"
)

# 1. Parse app/build.gradle.kts to get current versionCode and versionName
$gradlePath = "app/build.gradle.kts"
if (-not (Test-Path $gradlePath)) {
    Write-Error "Could not find build.gradle.kts at $gradlePath"
    exit 1
}

$content = Get-Content $gradlePath -Raw

if ($content -match "versionCode\s*=\s*(\d+)") {
    [int]$currentCode = $Matches[1]
} else {
    Write-Error "Could not find versionCode in build.gradle.kts"
    exit 1
}

if ($content -match 'versionName\s*=\s*"([^"]+)"') {
    $currentName = $Matches[1]
} else {
    Write-Error "Could not find versionName in build.gradle.kts"
    exit 1
}

# Parse version name components (Major.Minor.Patch)
$versionParts = $currentName.Split('.')
if ($versionParts.Length -ne 3) {
    Write-Error "Invalid versionName format: $currentName. Expected Major.Minor.Patch"
    exit 1
}

[int]$major = $versionParts[0]
[int]$minor = $versionParts[1]
[int]$patch = $versionParts[2]

# 2. Calculate new version according to the rules
[int]$newCode = $currentCode + 1

if ($Type -eq "major") {
    $major = $major + 1
    $minor = 0
    $patch = 0
} elseif ($Type -eq "minor") {
    $minor = $minor + 1
    $patch = 0
} else {
    # Default to small changes
    $patch = $patch + 1
}

$newName = "$major.$minor.$patch"

Write-Host "Bumping version from $currentName (Code: $currentCode) to $newName (Code: $newCode)"

# 3. Update build.gradle.kts
$newContent = $content -replace "versionCode\s*=\s*\d+", "versionCode = $newCode"
$newContent = $newContent -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$newName`""
Set-Content -Path $gradlePath -Value $newContent -NoNewline

# 4. Clean old release APK files
if (Test-Path "release") {
    # Git rm the old apk files so they don't leave untracked remnants in git history
    Get-ChildItem "release" -Filter "*.apk" | ForEach-Object {
        $relativePath = "release/" + $_.Name
        git rm $relativePath --ignore-unmatch -f
        Remove-Item $_.FullName -Force -ErrorAction Ignore
    }
} else {
    New-Item -ItemType Directory -Path "release" -Force
}

# 5. Build project
Write-Host "Running Gradle build..."
$buildResult = Start-Process -FilePath "cmd.exe" -ArgumentList "/c .\gradlew.bat assembleDebug" -NoNewWindow -Wait -PassThru
if ($buildResult.ExitCode -ne 0) {
    Write-Error "Gradle build failed!"
    exit 1
}

# 6. Copy new APK to release folder
$sourceApk = "app/build/outputs/apk/debug/app-debug.apk"
$destApk = "release/pdf-viewer-app-v$newName.apk"

if (Test-Path $sourceApk) {
    Copy-Item $sourceApk $destApk -Force
    Write-Host "Successfully copied new APK to $destApk"
} else {
    Write-Error "Compiled APK not found at $sourceApk"
    exit 1
}

# 7. Update release notes title
$notesPath = "release/release_notes.md"
if (Test-Path $notesPath) {
    $notesContent = Get-Content $notesPath -Raw
    $notesContent = $notesContent -replace "# Release v[^\s]+", "# Release v$newName"
    Set-Content -Path $notesPath -Value $notesContent -NoNewline
}

# 8. Git Add & Commit
Write-Host "Staging and pushing changes to GitHub..."
git add app/build.gradle.kts release/
git commit -m "release: v$newName (auto-build: $Type bump)"
git push origin main

# 9. Create GitHub Release and Upload APK
Write-Host "Checking git credentials for GitHub Release..."
try {
    $credInfo = echo "url=https://github.com" | git credential fill
    $token = ""
    foreach ($line in $credInfo) {
        if ($line.StartsWith("password=")) {
            $token = $line.Substring(9).Trim()
        }
    }

    if (-not [string]::IsNullOrEmpty($token)) {
        $env:GH_TOKEN = $token
        Write-Host "Creating GitHub Release v$newName and uploading APK..."
        
        # Check if gh CLI is installed
        $ghPath = "C:\Program Files\GitHub CLI\gh.exe"
        if (Test-Path $ghPath) {
            # Create the release and upload the APK
            & $ghPath release create "v$newName" $destApk --title "Release v$newName" --notes-file $notesPath --clobber
            Write-Host "Successfully created GitHub Release and uploaded APK!"
        } else {
            Write-Warning "GitHub CLI (gh.exe) was not found at $ghPath. Skipping release upload."
        }
    } else {
        Write-Warning "Could not retrieve git credentials token. Skipping GitHub Release creation."
    }
} catch {
    Write-Warning "Failed to automate GitHub Release: $_"
}

Write-Host "Release automation complete!"
