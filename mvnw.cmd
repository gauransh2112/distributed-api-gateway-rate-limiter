@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License me.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM
@REM Required ENV vars:
@REM JAVA_HOME - location of a JDK home directory
@REM
@REM Optional ENV vars
@REM MAVEN_BATCH_ECHO - set to 'on' to enable the echoing of the batch commands
@REM MAVEN_BATCH_PAUSE - set to 'on' to prompt at the end of the execution
@REM MAVEN_OPTS - parameters passed to the Java VM when running Maven
@REM MAVEN_SKIP_RC - flag to disable loading of mavenrc files
@REM ----------------------------------------------------------------------------

@if "%MAVEN_BATCH_ECHO%" == "on"  echo %MAVEN_BATCH_ECHO%
@if not "%MAVEN_BATCH_ECHO%" == "on" echo off

set MAVEN_PROJECTBASEDIR=%~dp0
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%

if not "%MAVEN_SKIP_RC%" == "" goto skipRcPre
if exist "%HOME%\mavenrc_pre.bat" call "%HOME%\mavenrc_pre.bat"
if exist "%HOME%\mavenrc_pre.cmd" call "%HOME%\mavenrc_pre.cmd"
if exist "%MAVEN_PROJECTBASEDIR%\mavenrc_pre.bat" call "%MAVEN_PROJECTBASEDIR%\mavenrc_pre.bat"
if exist "%MAVEN_PROJECTBASEDIR%\mavenrc_pre.cmd" call "%MAVEN_PROJECTBASEDIR%\mavenrc_pre.cmd"
:skipRcPre

setlocal enableextensions enabledelayedexpansion

if not "%JAVA_HOME%" == "" goto OkJHome
for %%i in (java.exe) do set "JAVACMD=%%~$PATH:i"
goto checkJCmd

:OkJHome
set "JAVACMD=%JAVA_HOME%\bin\java.exe"

:checkJCmd
if exist "%JAVACMD%" goto runWrapper

echo Error: JAVA_HOME is not defined correctly. >&2
echo   We cannot execute %JAVACMD% >&2
goto error

:runWrapper
set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"

if exist "%WRAPPER_JAR%" goto run

echo Could not find %WRAPPER_JAR%, attempting to download... >&2

set "DOWNLOAD_URL="
if exist "%WRAPPER_PROPERTIES%" (
    for /f "usebackq tokens=1,* delims==" %%A in ("%WRAPPER_PROPERTIES%") do (
        if "%%A"=="wrapperUrl" set "DOWNLOAD_URL=%%B"
    )
)
if "%DOWNLOAD_URL%"=="" set "DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"

if not exist "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper" mkdir "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper"

powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('%DOWNLOAD_URL%', '%WRAPPER_JAR%')"

if not exist "%WRAPPER_JAR%" (
    echo Error: Could not download %WRAPPER_JAR% >&2
    goto error
)

:run
"%JAVACMD%" -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %*
if ERRORLEVEL 1 goto error
goto end

:error
exit /b 1

:end
exit /b 0
