@Echo off
echo Apply Script: COPY
echo F|xcopy /y /s /f /q "%1" "%2"
echo F|xcopy /y /s /f /q "lint/in.jar" "release/latest/Origin-%3.jar"
echo Starting the Washing Machine
echo F|xcopy /y /s /f /q "lint/in.jar" "release/latest/Iris-%3.jar"
cd release
echo F|xcopy /y /s /f /q /E "latest" "%3/"
rmdir /Q/S latest