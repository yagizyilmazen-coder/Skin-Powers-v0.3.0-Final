@ECHO OFF
SETLOCAL
WHERE gradle >NUL 2>NUL
IF %ERRORLEVEL% NEQ 0 (
  ECHO [Skin Powers] Bu pakette Gradle Wrapper JAR bulunmuyor.
  ECHO GitHub Actions otomatik olarak Gradle 9.5.1 ile derler.
  ECHO Yerel derleme icin Gradle 9.5.1 ve Java 25 kurup: gradle build
  EXIT /B 1
)
gradle %*
ENDLOCAL
