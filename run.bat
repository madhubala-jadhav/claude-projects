@echo off
cd /d "%~dp0"
java -jar ExpenseInNutshell.jar %*
if errorlevel 1 (
  echo.
  echo Something went wrong. See the message above.
  pause
)
