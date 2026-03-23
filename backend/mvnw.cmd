@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM
@REM Optional ENV vars
@REM -----------------
@REM   MVNW_REPOURL - repo to use for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - username and password for downloading maven
@REM   MVNW_VERBOSE - true: enable verbose output
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET "__MVNW_ARG0_NAME__=%~nx0")
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_SAVE_ERRORLEVEL__=
@SET __MVNW_SAVE_EXTENSIONS__=
@SETLOCAL enableextensions
@SET __MVNW_SAVE_EXTENSIONS__=Y
@SET __MVNW_CMD__=MAVEN

SET MAVEN_PROJECTBASEDIR=%~dp0
IF NOT "%MAVEN_BASEDIR%" == "" SET MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%
@SETLOCAL

SET WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar
SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain
SET WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar

FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties") DO (
    IF "%%A"=="wrapperUrl" SET WRAPPER_URL=%%B
)

@IF "%MVNW_VERBOSE%" == "true" (
  @ECHO Wrapper URL: %WRAPPER_URL%
)

IF NOT EXIST %WRAPPER_JAR% (
  IF "%MVNW_VERBOSE%" == "true" (
    @ECHO Downloading: %WRAPPER_URL%
  )
  PowerShell -Command "&{"^
    "$webclient = new-object System.Net.WebClient;"^
    "if (-not ([string]::IsNullOrEmpty('%MVNW_USERNAME%') -and [string]::IsNullOrEmpty('%MVNW_PASSWORD%'))) {"^
    "$webclient.Credentials = new-object System.Net.NetworkCredential('%MVNW_USERNAME%', '%MVNW_PASSWORD%');"^
    "}"^
    "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $webclient.DownloadFile('%WRAPPER_URL%', '%WRAPPER_JAR%')"^
    "}"
  IF "%ERRORLEVEL%"=="0" ( GOTO :continue_download )
  DEL /F /Q "%WRAPPER_JAR%"
  @ECHO Could not download %WRAPPER_URL%
  EXIT /B 1
  :continue_download
)

@IF "%MVNW_VERBOSE%" == "true" (
  @ECHO JAVA_HOME=%JAVA_HOME%
)

@SET MAVEN_JAVA_EXE="%JAVA_HOME%\bin\java.exe"
SET WRAPPER_LAUNCHER_ARGS=-Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%"

SET DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip

FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties") DO (
    IF "%%A"=="distributionUrl" SET DOWNLOAD_URL=%%B
)

SET MAVEN_DIST_NAME=%DOWNLOAD_URL:~0,-4%
FOR %%i IN ("%MAVEN_DIST_NAME%") DO SET "MAVEN_DIST_NAME=%%~ni"
SET MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\%MAVEN_DIST_NAME%

@IF "%MVNW_VERBOSE%" == "true" (
  @ECHO MAVEN_HOME=%MAVEN_HOME%
)

IF NOT EXIST "%MAVEN_HOME%\bin\mvn.cmd" (
  IF "%MVNW_VERBOSE%" == "true" (
    @ECHO Downloading Maven distribution: %DOWNLOAD_URL%
  )
  PowerShell -Command "&{"^
    "$webclient = new-object System.Net.WebClient;"^
    "if (-not ([string]::IsNullOrEmpty('%MVNW_USERNAME%') -and [string]::IsNullOrEmpty('%MVNW_PASSWORD%'))) {"^
    "$webclient.Credentials = new-object System.Net.NetworkCredential('%MVNW_USERNAME%', '%MVNW_PASSWORD%');"^
    "}"^
    "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12;"^
    "$webclient.DownloadFile('%DOWNLOAD_URL%', '%MAVEN_HOME%.zip')"^
    "}"
  IF "%ERRORLEVEL%"=="0" ( GOTO :continue_mvn_download )
  DEL /F /Q "%MAVEN_HOME%.zip"
  @ECHO Could not download %DOWNLOAD_URL%
  EXIT /B 1
  :continue_mvn_download
  PowerShell -Command "& { Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('%MAVEN_HOME%.zip', '%USERPROFILE%\.m2\wrapper\dists'); }"
)

@SET MAVEN_JAVA_EXE="%JAVA_HOME%\bin\java.exe"
%MAVEN_JAVA_EXE% %WRAPPER_LAUNCHER_ARGS% -classpath "%WRAPPER_JAR%" %WRAPPER_LAUNCHER% %MAVEN_CONFIG% %*
IF ERRORLEVEL 1 GOTO error
GOTO end

:error
SET ERROR_CODE=%ERRORLEVEL%

:end
@ENDLOCAL & SET ERROR_CODE=%ERROR_CODE%

IF NOT "%MVNW_SAVE_ERRORLEVEL%"=="" SET ERRORLEVEL=%MVNW_SAVE_ERRORLEVEL%
IF NOT "%MVNW_SAVE_EXTENSIONS%"=="" @ENDLOCAL

EXIT /B %ERROR_CODE%
