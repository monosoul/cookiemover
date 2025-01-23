1. Install Firefox Developer Edition: https://www.mozilla.org/en-US/firefox/developer/
2. Open Firefox DE and go to about:config, set xpinstall.signatures.required to false
3. Restart Firefox
4. Go to about:addons, click the gear button at the top right and select "Install Add-on From File..."
5. In the file picker window, find cookiemover-extension.zip and select it
6. Confirm Add-on installation

7. Copy firefox/cookiemover.json into ~/Library/Application Support/Mozilla/NativeMessagingHosts/ .
If directory NativeMessagingHosts doesn't exist yet, create it.

mkdir ~/Library/Application Support/Mozilla/NativeMessagingHosts
cp firefox/cookiemover.json ~/Library/Application Support/Mozilla/NativeMessagingHosts/cookiemover.json

8. Copy cookiemover executable into /opt/ .

sudo cp ./cookiemover /opt/cookiemover

9. Done, now whenever you need to authenticate with Chrome and move cookies,
simply click the cookiemover extension button in Firefox and proceed with Authentication.
