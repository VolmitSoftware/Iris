@Echo off
echo Apply Script: COPY
echo F|xcopy /y /s /f /q "%1" "%2"
echo F|xcopy /y /s /f /q "lint/in.jar" "release/latest/Origin-%3.jar"
echo Starting the Washing Machine
cd lint
java -Xmx4g -Xms1m -jar proguard.jar @proguard.conf
cd ..
echo F|xcopy /y /s /f /q "lint/out.jar" "release/latest/Iris-%3.jar"
echo F|xcopy /y /s /f /q "lint/mapping.txt" "release/latest/mapping-%3.txt"
cd release
echo F|xcopy /y /s /f /q /E "latest" "%3/"
rmdir /Q/S latest