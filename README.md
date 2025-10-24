# AuroraStore - Whitelist Edition

A simplified Aurora Store fork that shows **ONLY whitelisted apps** with automatic remote whitelist updates. Perfect for curated app distribution to specific devices or user groups.

## What is Whitelist Mode?

**IMPORTANT:** This is a WHITELIST system - shows ONLY apps that are in your JSON list.

If your whitelist.json is empty or unreachable, NO APPS will be shown.

## Features

### Core Functionality
- **Apps Tab** - Displays whitelisted apps
- **Updates Tab** - Shows available updates for installed whitelisted apps
- **Auto-whitelist sync** - Fetches remote whitelist every 15 seconds
- **Automatic UI refresh** - Updates immediately when remote whitelist changes
- **Smart change detection** - Only refreshes when whitelist actually changes
- **Anonymous login** - Uses Spoof Manager fallback for Play Store access

### What's NOT Included
- ❌ No Play Store browsing/categories
- ❌ No app discovery or search across the entire store
- ❌ No recommendations or featured apps
- ✅ Only whitelisted apps are accessible

## Building the App

### GitHub Actions Auto-Build

The repository automatically builds APKs when you push changes to the `main` branch:

1. **Push changes** to trigger build
2. **Go to Actions tab** in your GitHub repository
3. **Download artifacts** from the completed workflow

**Builds are triggered by:**
- Changes to `.kt`, `.java`, `.xml`, `.gradle` files
- **NOT triggered by** README, docs, or other non-code changes

### Local Build

#### Prerequisites
- **Java 21** (OpenJDK recommended)
- **Android SDK** (API level 34+)
- **Git**

1. **Clone the repository:**
```bash
git clone https://github.com/alltechdev/AuroraStore-WhitelistEdition.git
cd AuroraStore-WhitelistEdition
```

2. **Set Java 21:**
```bash
export JAVA_HOME=/path/to/jdk-21
export PATH=$JAVA_HOME/bin:$PATH
```

3. **Build APKs:**
```bash
# Debug build (for testing)
./gradlew assembleVanillaDebug

# Release build (for distribution)
./gradlew assembleVanillaRelease
```

4. **Find built APKs:**
- Debug: `app/build/outputs/apk/vanilla/debug/`
- Release: `app/build/outputs/apk/vanilla/release/`

## Configuring Whitelist URLs

The app fetches whitelist data from a remote JSON source. You can configure different whitelist URLs:

### Default Configuration

The app uses this default GitHub API URL:
```
https://api.github.com/repos/alltechdev/alltech.dev/contents/whitelist.json?ref=main
```

### Setting Up Your Own Whitelist

#### Option 1: Fork This Repository (Recommended)

1. **Fork this repository** to your GitHub account
2. **Create a whitelist repository** with a `whitelist.json` file
3. **Format your JSON** as an array of package names:
```json
[
  "com.example.app1",
  "com.example.app2",
  "com.another.package"
]
```

4. **Update the default URL** by modifying this line in `app/src/main/java/com/aurora/store/data/providers/RemoteWhitelistProvider.kt`:
```kotlin
var remoteWhitelistUrl: String
    get() = Preferences.getString(
        context,
        Preferences.PREFERENCE_REMOTE_WHITELIST_URL,
        "https://api.github.com/repos/YOUR_USERNAME/YOUR_REPO/contents/whitelist.json?ref=main"
    )
```

5. **Push changes** to your fork - GitHub Actions will automatically build your customized APK

### Whitelist JSON Format

Your whitelist JSON must be an array of package names with optional categories:

#### Simple Format (No Categories)
```json
[
  "com.package.name1",
  "com.package.name2",
  "com.another.package"
]
```

#### With Categories (Apps Tab Only)
```json
[
  "com.package.name1 Productivity",
  "com.package.name2 Games",
  "com.another.package Social",
  "com.nocategory.app"
]
```

**Category Format:** `"package.name CategoryName"`
- Everything after the first space is the category name
- Apps without a space are placed in "Other" category
- Categories only appear in the Apps tab
- Updates tab ignores categories completely
- Categories are sorted alphabetically, with "Other" last

#### With External Apps (feature/external-apps branch)
```json
[
  "com.playstore.app Games",
  "Uber|com.uber.app|1.2.3|https://example.com/uber.apk|https://example.com/icon.png|Transport",
  "Signal|org.thoughtcrime.securesms|5.4.3|https://signal.org/app.apk|https://signal.org/icon.png|Social"
]
```

**External App Format:** `"AppName|packageName|version|apkUrl|iconUrl|category"`
- AppName: Display name (e.g., "Uber")
- packageName: Android package identifier
- version: Version string (e.g., "1.2.3") for update detection
- apkUrl: Direct download URL for APK file
- iconUrl: Direct URL to app icon (optional, leave empty for no icon)
- category: Category name (optional)

**External App Features:**
- Download APKs from any URL (not just Play Store)
- Version checking: compares installed version vs JSON version
- Shows in Apps tab with custom name and icon
- Shows in Updates tab when newer version available
- Installation works same as Play Store apps

## How Auto-Updates Work

1. **App starts** → Immediate whitelist fetch from configured URL
2. **Every 15 seconds** → Background fetch and comparison
3. **If changes detected** → Update local whitelist + emit event
4. **Apps/Updates tabs receive event** → Automatically refresh app list
5. **User sees changes** → No manual action required

---

## License

This project maintains the same license as the original Aurora Store.
