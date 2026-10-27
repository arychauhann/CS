## Features
- Sign in and account registration against `/auth/login` and `/auth/register`
- Persistent bearer token stored in Android SharedPreferences
- Project list, create project, GitHub import
- Project bot status/logs and start/stop/restart controls
- Isolated shell command interface
- Project file tree, file reading/editing/saving, create file/folder
- Read-only shared-source browser
- Project issue listing and creation
- Change password

## Build
Open this folder in Android Studio (JDK 17 recommended), allow Gradle sync, then run `:app:assembleRelease`.

