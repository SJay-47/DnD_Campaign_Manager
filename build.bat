@echo off
if not exist out mkdir out
javac -cp "lib\ojdbc11.jar" -d out src\*.java
if %errorlevel%==0 echo Build OK
pause
