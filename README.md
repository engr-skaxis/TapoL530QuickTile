# Tapo L530 Quick Tile

## Phone-only build with GitHub Actions

No Android Studio or AndroidIDE is required.

### 1. Put this project in a GitHub repository
From your phone, create a new GitHub repository and upload the contents of this folder.

The `.github/workflows/build-apk.yml` file must be uploaded too.

### 2. Build
Open the repository on GitHub:
Actions → Build Tapo L530 APK → Run workflow.

Wait for the workflow to finish with a green check.

### 3. Download the APK
Open the completed workflow run. Under Artifacts, tap:
Tapo-L530-QuickTile-debug

Download and extract the ZIP, then install the APK on your phone.

### 4. Configure the app
Open Tapo L530 Quick Tile and enter:
- L530 local IP
- Tapo email
- Tapo password

Enable Tapo → Me → Third-Party Services → Third-Party Compatibility.

Then use "Add L530 to Quick Panel" and/or edit Samsung Quick Panel to add L530.

The project contains no Tapo credentials. Credentials are entered only after the APK is installed and are stored using Android Keystore-backed encryption.

### Important
This project has not been physically tested against an L530 in this environment. The build workflow only compiles the project; it does not prove compatibility with every L530 firmware version.
