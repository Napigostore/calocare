# CaloCare iOS Build & TestFlight Deployment

## Prerequisites
- macOS with Xcode 14+
- Apple Developer Account with Team ID
- Xcode Command Line Tools: `xcode-select --install`
- Ruby 2.7+ (for fastlane)

## Setup (First Time Only)

### 1. Install Dependencies
```bash
cd ios/fastlane
bundle install
```

### 2. Configure Backend (if needed)
```bash
cp Config/Backend.xcconfig.example Config/Backend.xcconfig
# Edit Backend.xcconfig with your backend URL and app key
```

### 3. Setup App Store Connect Authentication
```bash
# Option A: Using API Key (Recommended)
fastlane spaceauth -u your-apple-id@example.com

# Option B: App Store Connect API (see fastlane docs)
```

## Building for TestFlight

### Quick Deploy (Recommended)
```bash
cd ios
fastlane testflight
```

This will:
1. ✅ Generate Xcode project from project.yml
2. ✅ Build release binary
3. ✅ Create archive
4. ✅ Upload to TestFlight
5. ✅ Notify external testers

### Manual Build (If fastlane fails)
```bash
cd ios

# Generate Xcode project
xcodegen generate

# Build archive
xcodebuild -workspace CaloCare.xcworkspace \
  -scheme CaloCare \
  -configuration Release \
  -destination 'platform=iOS' \
  -archivePath build/CaloCare.xcarchive \
  archive

# Export IPA
xcodebuild -exportArchive \
  -archivePath build/CaloCare.xcarchive \
  -exportOptionsPlist Config/ExportOptions.plist \
  -exportPath build
```

## Checking TestFlight Status

### Via Fastlane
```bash
cd ios
fastlane testflight_status
```

### Via App Store Connect Web
1. Open: https://appstoreconnect.apple.com
2. Go to: MyApps > CaloCare > TestFlight > Builds
3. Check build status and review stage

### Via Command Line
```bash
# Using xcrun altool (requires credentials)
xcrun altool --list-builds \
  --bundle-id com.calocare.app \
  -u your-apple-id@example.com \
  -p your-app-specific-password
```

## Troubleshooting

### Build Fails with Code Signing Error
```bash
# Check provisioning profiles
fastlane sigh renew -a com.calocare.app --force

# Update signing settings
fastlane match appstore --force_for_new_devices
```

### TestFlight Upload Fails
- Check App Store Connect for app status
- Verify bundle ID matches provisioning profile
- Ensure version/build numbers are incremented

### Review Takes Long
**Current Status: Checking...**
- Apple typically reviews in 24-48 hours
- Complex app features may take longer
- Pro features (AI, health data) may require additional review

## Version Info
- **Current Version**: 1.4.0
- **Build Number**: 50 (Apple already has 1.0.0 build 49; a new build must be higher)
- **Features**: Recipe Builder, Shopping Lists, Analytics, Pro Features

## Support
For issues, check:
- Xcode build logs: `~/Library/Logs/xcrun/`
- Fastlane logs: `build/fastlane-activity.log`
- App Store Connect notifications
