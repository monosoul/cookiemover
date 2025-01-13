function getActiveTab() {
    return browser.tabs.query({active: true, currentWindow: true});
}

function onResponse(tab) {
    return function (response) {
        console.log("Importing cookies")
        response.map((cookie) => {
            return {
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
        }).forEach((cookie) => {
            browser.cookies.remove({name: cookie.name, url: tab.url});
            browser.cookies.set(cookie);
        })

        browser.tabs.reload(tab.id).then(() => {
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
browser.browserAction.onClicked.addListener(() => {
    console.log("Calling cookie mover");
    getActiveTab().then((tabs) => {
        let tab = tabs[0]
        let sending = browser.runtime.sendNativeMessage("cookiemover", {"url": `${tab.url}`});

        sending.then(onResponse(tab), onError);
    })
});
