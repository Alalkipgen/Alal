@rem Gradle start-up script for Windows (Alal, compact wrapper launcher).
@if "%DEBUG%"=="" @echo off
setlocal

set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if exist "%WRAPPER_JAR%" goto haveJar
where gradle >NUL 2>&1
if errorlevel 1 (
  echo ERROR: gradle\wrapper\gradle-wrapper.jar is missing and no 'gradle' binary is installed.
  echo        Install Gradle 8.9 and run: gradle wrapper --gradle-version 8.9
  exit /b 1
)
echo gradle-wrapper.jar not found; generating it with the installed Gradle...
pushd "%APP_HOME%"
call gradle wrapper --gradle-version 8.9 --distribution-type bin -q
popd

:haveJar
set JAVA_EXE=java.exe
if not "%JAVA_HOME%"=="" set JAVA_EXE=%JAVA_HOME%\bin\java.exe

"%JAVA_EXE%" -Xmx64m -Xms64m %JAVA_OPTS% %GRADLE_OPTS% -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
