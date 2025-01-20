function getActiveTab() {
    return browser.tabs.query({active: true, currentWindow: true});
}

function onResponse(oldCookies, tab) {
    return function (response) {
        console.log("Removing old cookies")
        oldCookies.forEach(cookie => {
            browser.cookies.remove({name: cookie.name, url: tab.url});
        })
        console.log("Importing new cookies")
        response.cookies.map((cookie) => {
            let newCookie = {
                domain: cookie.domain || '',
                name: cookie.name || '',
                value: cookie.value || '',
                path: cookie.path || null,
                secure: cookie.secure || null,
                httpOnly: cookie.httpOnly || null,
                expirationDate: cookie.expirationDate || null,
                sameSite: cookie.sameSite || null,
                storeId: tab.cookieStoreId || null,
                url: tab.url,
            };
            if (cookie.sameSite === "unspecified") {
                newCookie.sameSite = null;
            }
            return newCookie;
        }).forEach((cookie) => {
            browser.cookies.remove({name: cookie.name, url: response.targetUrl});
            browser.cookies.set(cookie);
        })

        browser.tabs.update(tab.id, {active: true, url: `${response.targetUrl}`}).then(() => {
            browser.windows.update(tab.windowId, {focused: true});
        });
    }
}

function onError(error) {
    console.log(`Error: ${error}`);
}

/*
On a click on the browser action, send the app a message.
*/
async function onBrowserActionClick() {
    console.log("Calling cookie mover");
    let tabs = await getActiveTab()
    let tab = tabs[0]

    let options = await browser.storage.sync.get({
        authDomain: "okta.com",
        chromeExecPath: "",
        chromeDataDirPath: "",
        appDataDirPath: "",
    });

    let cookies = await browser.cookies.getAll({url: tab.url})
    let sending = browser.runtime.sendNativeMessage("cookiemover", {
        "url": `${tab.url}`,
        "existingCookies": cookies,
        "authDomain": options.authDomain,
        "chromeExecPath": options.chromeExecPath,
        "chromeDataDirPath": options.chromeDataDirPath,
        "appDataDirPath": options.appDataDirPath,
    });

    sending.then(onResponse(cookies, tab), onError);
}

browser.browserAction.onClicked.addListener(onBrowserActionClick);
