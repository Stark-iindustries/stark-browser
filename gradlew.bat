@rem
@rem Copyright 2015 the original author or authors.
@rem
@echo off
set APP_HOME=%~dp0
set JAVA_EXE=java.exe
if defined JAVA_HOME (set JAVA_EXE=%JAVA_HOME%\bin\java.exe)
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
