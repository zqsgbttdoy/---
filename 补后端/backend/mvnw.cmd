@REM Maven Wrapper startup batch script
@REM This script downloads Maven if not present and runs the build

@echo off
setlocal

set MAVEN_VERSION=3.9.6
set MAVEN_URL=https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip
set MAVEN_HOME=%~dp0.mvn\maven\apache-maven-%MAVEN_VERSION%

if exist "%MAVEN_HOME%\bin\mvn.cmd" goto runMaven

echo Maven not found, downloading Maven %MAVEN_VERSION%...
if not exist "%~dp0.mvn\maven" mkdir "%~dp0.mvn\maven"

powershell -Command "Invoke-WebRequest -Uri '%MAVEN_URL%' -OutFile '%~dp0.mvn\maven\maven.zip'"
powershell -Command "Expand-Archive -Path '%~dp0.mvn\maven\maven.zip' -DestinationPath '%~dp0.mvn\maven' -Force"
del "%~dp0.mvn\maven\maven.zip"

:runMaven
"%MAVEN_HOME%\bin\mvn.cmd" %*
