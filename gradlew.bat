@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@setlocal

set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

set JAVA_EXE=java.exe
if defined JAVA_HOME set JAVA_EXE=%JAVA_HOME%\bin\java.exe

if exist "%CLASSPATH%" goto execute

echo gradlew: wrapper jar missing; attempting bootstrap...
mkdir "%APP_HOME%\gradle\wrapper" 2> NUL
for %%U in (https://raw.githubusercontent.com/gradle/gradle/v8.7/gradle/wrapper/gradle-wrapper.jar) do (
  powershell -Command "try { (New-Object Net.WebClient).DownloadFile('%%U', '%CLASSPATH%') } catch { }"
)

:execute
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -Dorg.gradle.appname=%APP_BASE_NAME% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

@endlocal

