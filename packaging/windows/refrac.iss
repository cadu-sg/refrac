; Inno Setup script for refrac-windows-setup.exe. Compile after PyInstaller
; has built dist\refrac, from the repository root:
;
;     iscc /DAppVersion=1.0 packaging\windows\refrac.iss
;
; The installer lands in dist\. DistDir and OutputDir override the folders,
; relative to this script's folder. It installs for the current user only,
; so it needs no administrator rights.

#ifndef AppVersion
  #define AppVersion "dev"
#endif
#ifndef DistDir
  #define DistDir "..\..\dist\refrac"
#endif
#ifndef OutputDir
  #define OutputDir "..\..\dist"
#endif

[Setup]
; The AppId identifies refrac across versions: a newer installer replaces the
; installed version. Never change it.
AppId={{6E0C9B52-4C0A-4F7E-9D3B-2A8F1E5C7D41}
AppName=refrac
AppVersion={#AppVersion}
AppVerName=refrac {#AppVersion}
AppPublisher=cadu-sg
AppPublisherURL=https://github.com/cadu-sg/refrac
AppSupportURL=https://github.com/cadu-sg/refrac
DefaultDirName={autopf}\refrac
DefaultGroupName=refrac
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0.17763
OutputDir={#OutputDir}
OutputBaseFilename=refrac-windows-setup
SetupIconFile=..\icon\refrac.ico
UninstallDisplayIcon={app}\refrac.exe
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ShowLanguageDialog=auto

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "brazilianportuguese"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#DistDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
; Files of the previous version that this one no longer has
Type: filesandordirs; Name: "{app}\_internal"

[Icons]
Name: "{autoprograms}\refrac"; Filename: "{app}\refrac.exe"
Name: "{autodesktop}\refrac"; Filename: "{app}\refrac.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\refrac.exe"; Description: "{cm:LaunchProgram,refrac}"; Flags: nowait postinstall skipifsilent
