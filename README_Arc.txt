1. Unpack cookiemover-extension.zip
2. Navigate to arc://extensions/
3. Enable developer mode
4. Click "Load unpacked"
5. Select the directory with unpacked cookiemover-extension

6. Copy chrome/cookiemover.json into ~/Library/Application Support/Google/Chrome/NativeMessagingHosts/ .
If directory NativeMessagingHosts doesn't exist yet, create it.

mkdir ~/Library/Application Support/Google/Chrome/NativeMessagingHosts/
cp cookiemover.json ~/Library/Application Support/Google/Chrome/NativeMessagingHosts/cookiemover.json

7. Copy cookiemover executable into /opt/ .

sudo cp ./cookiemover /opt/cookiemover

8. Done, now whenever you need to authenticate with Chrome and move cookies,
simply click the cookiemover extension button in Arc and proceed with Authentication.
