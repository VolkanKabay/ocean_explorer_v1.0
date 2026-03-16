# Ocean-Server mit Autostart
Start-Process powershell -ArgumentList 'java -jar ".\oceanserver.jar" -autostart'

# Backend
Start-Process powershell -ArgumentList 'java -cp ".;libs/json.jar;libs/mysql-connector-j-8.3.0.jar" shipapp.ShipAppApiServer'

# Frontend
Start-Process powershell -WorkingDirectory '.\shipapp-ui' -ArgumentList 'npm run dev'