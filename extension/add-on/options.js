async function saveOptions(e) {
    e.preventDefault();
    await browser.storage.sync.set({
        authDomain: document.querySelector("#auth-domain").value,
        chromeExecPath: document.querySelector("#chrome-exec-path").value,
        chromeDataDirPath: document.querySelector("#chrome-data-dir-path").value,
        appDataDirPath: document.querySelector("#app-data-dir-path").value,
    });
}

async function restoreOptions() {
    let res = await browser.storage.sync.get({
        authDomain: "okta.com",
        chromeExecPath: "",
        chromeDataDirPath: "",
        appDataDirPath: "",
    });
    document.querySelector("#auth-domain").value = res.authDomain;
    document.querySelector("#chrome-exec-path").value = res.chromeExecPath;
    document.querySelector("#chrome-data-dir-path").value = res.chromeDataDirPath;
    document.querySelector("#app-data-dir-path").value = res.appDataDirPath;
}

document.addEventListener('DOMContentLoaded', restoreOptions);
document.querySelector("form").addEventListener("submit", saveOptions);