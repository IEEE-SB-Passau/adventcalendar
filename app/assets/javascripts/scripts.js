// activate source highlighting
hljs.initHighlightingOnLoad();

// activate the pretty time display
$(document).ready(function () {
    $('abbr.timeago').timeago();
});

// activate diff
$(document).ready(function () {
    applyDiff();
});

// activate pretty file input
$(document).ready(function () {
    const fileinputFilenameUpdate = function () {
        const file = $(this)[0].files[0];
        $(this).next('.fileinput-fake').find('.fileinput-filename').text(file !== undefined ? file.name : '');
    };
    const fileinputElements = $('.fileinput-input');
    fileinputElements.on('change', fileinputFilenameUpdate);
    fileinputElements.each(fileinputFilenameUpdate);
});

// Enable summernote on all textareas with class 'wysiwyg'
$(document).ready(function () {
    $('textarea.wysiwyg').summernote();
});

function shouldBePrefixed(url, baseUrl) {
    return !url.match(/^(?:https?:\/\/|mailto:).*$/) && !url.startsWith(baseUrl);
}

// file loader for loading text files into text areas
function loadFileAsText(input, textarea) {
    const fileToLoad = input.files[0];

    if (fileToLoad !== undefined) {
        const fileReader = new FileReader();
        fileReader.onload = function (fileLoadedEvent) {
            const text = fileLoadedEvent.target.result;

            // special handling for wysiwyg editor
            if (textarea.classList.contains("wysiwyg")) {

                const domElement = new DOMParser().parseFromString(text, "text/html");
                const baseUrl = jsRoutes.org.ieee_passau.controllers.CmsController.calendar().url;
                let urlFixes = 0;
                $("a", domElement).attr("href", (i, url) => {
                    if (shouldBePrefixed(url, baseUrl)) {
                        console.log(`changing "${url}" to "${baseUrl + url}"`);
                        urlFixes++;
                        url = baseUrl + url;
                    }
                    return url;
                });
                $("img", domElement).attr("src", (i, url) => {
                    if (shouldBePrefixed(url, baseUrl)) {
                        console.log(`changing "${url}" to "${baseUrl + url}"`);
                        urlFixes++;
                        url = baseUrl + url;
                    }
                    return url;
                });

                $('.wysiwyg').summernote('code', domElement.body.innerHTML);

                if (urlFixes > 0) {
                    alert(`${urlFixes} URLs were prefixed with "${baseUrl}", listed in the console.`);
                }
            } else {
                textarea.value = text;
            }
        };
        fileReader.readAsText(fileToLoad, "UTF-8");
    }
}

// fix summernote link mangling
$('.wysiwyg').summernote({
    onCreateLink: function (url) {
        const baseUrl = jsRoutes.org.ieee_passau.controllers.CmsController.calendar().url;
        if (shouldBePrefixed(url, baseUrl)) {
            url = baseUrl + url;
        }
        return url;
    }
});

// activate cookie notice
window.addEventListener("load", function () {
    window.cookieconsent.initialise({
        "palette": {
            "popup": {
                "background": "#c9d9e5",
                "text": "#000000"
            },
            "button": {
                "background": "#ffffff",
                "text": "#000000"
            }
        },
        "theme": "classic",
        "content": {
            "message": cookieText,
            "dismiss": cookieButton,
            "link": cookieLink,
            "href": jsRoutes.org.ieee_passau.controllers.CmsController.content("contact").url
        }
    });
});

// activate notification dismissal
$('#flash-notification').on('close.bs.alert', function () {
    $.post(jsRoutes.org.ieee_passau.controllers.UserController.dismissNotification().url);
});
