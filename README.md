# MovieBox (TMDB + Apple previews): Windows exe + Android TV apk

Tabs: Home, Short TV, Regional, Originals, Music.

## Get MovieBox.exe (no installs, no .bat files)
1. Create a free GitHub account and a new repository (Public or Private).
2. Upload everything in this folder (Add file -> Upload files; drag the whole folder content, including the hidden ".github" folder).
   If ".github" is skipped by the browser: Add file -> Create new file, name it
   `.github/workflows/build.yml` and paste the content of that file from this zip.
3. Wait ~3-5 minutes (Actions tab shows "Build MovieBox.exe").
4. Open the repo -> Releases -> "MovieBox (latest build)" -> download MovieBox.exe. Double-click to run.

Every time files change and are pushed/uploaded again, GitHub rebuilds and replaces MovieBox.exe at the same Releases link.

## First run
Click the gear icon and paste your free TMDB API key (themoviedb.org -> Settings -> API).

## Developer run (optional)
`npm install` then `npm start`.

Attribution: movie/TV data and images from TMDB; streaming availability from JustWatch; music previews and artwork from Apple.

## Android TV apk
Built automatically with the exe: Releases -> MovieBox-TV.apk. Sideload on the TV (Downloader app / USB / adb install). Works with the TV remote (D-pad, OK, Back).
