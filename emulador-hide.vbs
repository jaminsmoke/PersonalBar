' Arranque limpio del tablet: UI del emulador visible, sin consola
' (Windows Terminal se engancha a emulator.exe si se usa `start`).
Option Explicit
Dim sh, fso, sdk, emu, bat, mode
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
sdk = sh.ExpandEnvironmentStrings("%LOCALAPPDATA%") & "\Android\Sdk"
emu = sdk & "\emulator\emulator.exe"
bat = fso.BuildPath(fso.GetParentFolderName(WScript.ScriptFullName), "emulador.bat")

mode = ""
If WScript.Arguments.Count > 0 Then mode = LCase(WScript.Arguments(0))

If mode = "config" Then
  sh.Run """" & bat & """ -config", 0, False
  WScript.Quit 0
End If

If mode = "start" Then
  ' 0 = ocultar la consola de emulator.exe; qemu sigue mostrando el tablet.
  sh.Run """" & emu & """ -avd Tablet-PixelTablet -port 5558 -timezone Europe/Madrid -change-locale es-ES", 0, False
  HideEmulatorConsole
  WScript.Quit 0
End If

WScript.Quit 1

Sub HideEmulatorConsole()
  Dim ps
  ps = "Add-Type -Name W -Namespace Native -MemberDefinition '[DllImport(""user32.dll"")] public static extern bool ShowWindow(IntPtr h,int n);'; " & _
       "Get-Process WindowsTerminal,emulator,cmd -ErrorAction SilentlyContinue | " & _
       "Where-Object { $_.MainWindowTitle -like '*emulator.exe*' } | " & _
       "ForEach-Object { [Native.W]::ShowWindow($_.MainWindowHandle, 0) | Out-Null }"
  sh.Run "powershell.exe -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -Command " & Chr(34) & ps & Chr(34), 0, False
End Sub
