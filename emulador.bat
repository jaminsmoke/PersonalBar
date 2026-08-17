@echo off
setlocal EnableExtensions
set "SDK=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%SDK%\platform-tools\adb.exe"
set "PORT=5558"
set "SERIAL=emulator-%PORT%"

rem Tablet-PixelTablet (Bar): UI visible, consola de emulator.exe oculta.
rem Puerto fijo 5558 para no chocar con el movil (5554).

if "%~1"=="-config" goto config

"%ADB%" devices | findstr /c:"%SERIAL%" >nul 2>&1
if errorlevel 1 (
  wscript //nologo "%~dp0emulador-hide.vbs" start
)

wscript //nologo "%~dp0emulador-hide.vbs" config
exit /b 0

:config
set /a TRIES=0
:findtab
set /a TRIES+=1
if %TRIES% gtr 60 ( exit /b 1 )
set "FOUND="
for /f "tokens=1,2" %%d in ('"%ADB%" devices') do (
  if "%%d"=="%SERIAL%" if "%%e"=="device" set "FOUND=1"
)
if not defined FOUND ( ping -n 3 127.0.0.1 >nul & goto findtab )

set /a BOOT_TRIES=0
:bootloop
set /a BOOT_TRIES+=1
if %BOOT_TRIES% gtr 90 ( exit /b 1 )
for /f "tokens=* delims=" %%b in ('"%ADB%" -s %SERIAL% shell getprop sys.boot_completed 2^>nul') do set "BOOTED=%%b"
if not "%BOOTED%"=="1" ( ping -n 3 127.0.0.1 >nul & goto bootloop )

"%ADB%" -s %SERIAL% shell settings put system system_locales es-ES
exit /b 0
