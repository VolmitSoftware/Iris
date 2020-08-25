@Echo off
echo Apply Script: COPY
echo F|xcopy /y /s /f /q "%1" "%2"
echo F|xcopy /y /s /f /q "lint/in.jar" "release/latest/Origin-%3.jar"
echo Starting the Washing Machine
cd lint

echo ZKM Rinse Cycle
java -Xmx4g -Xms1m -jar ZKM.jar script.zkm
echo F|xcopy /y /f /q "out/in.jar" "out.jar"

cd ..
echo F|xcopy /y /s /f /q "lint/out.jar" "release/latest/Iris-%3.jar"
cd release
echo F|xcopy /y /s /f /q /E "latest" "%3/"