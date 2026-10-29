$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$runtime=Join-Path $PSScriptRoot '.venv/Scripts/python.exe'
if(-not (Test-Path -LiteralPath $runtime)){throw 'Tạo môi trường backend/.venv và cài requirements.txt trước. Xem docs/SETUP_FIREBASE_MAPS.md.'}
$serviceAccount=Join-Path $PSScriptRoot 'service-account.json'
if((Test-Path -LiteralPath $serviceAccount) -and -not $env:GOOGLE_APPLICATION_CREDENTIALS){$env:GOOGLE_APPLICATION_CREDENTIALS=$serviceAccount}
if(-not $env:GOOGLE_APPLICATION_CREDENTIALS){
    $pathFile=Join-Path $PSScriptRoot 'firestore-credentials.path'
    if(Test-Path -LiteralPath $pathFile){$env:GOOGLE_APPLICATION_CREDENTIALS=(Get-Content -LiteralPath $pathFile -Raw).Trim()}
}
if(-not $env:GOOGLE_APPLICATION_CREDENTIALS -or -not (Test-Path -LiteralPath $env:GOOGLE_APPLICATION_CREDENTIALS)){
    throw 'Cần khóa Firebase Admin. Đặt đường dẫn tệp JSON trong biến GOOGLE_APPLICATION_CREDENTIALS hoặc backend/firestore-credentials.path.'
}
& $runtime server.py --host 0.0.0.0 --port 8080

