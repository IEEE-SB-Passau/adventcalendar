// activate source highlighting
hljs.initHighlightingOnLoad();

$(document).ready(function () {
    // activate the datetimepickers
    $('.datetimepicker').datetimepicker();
    $('abbr.timeago').timeago();

    // activate diff
    applyDiff();
});

// activate pretty file input
$(document).on('change', '.btn-file :file', function() {
    $(this).trigger('fileselect', $(this).val().replace(/\\/g, '/').replace(/.*\//, ''));
});

$(document).ready(function() {
    $('.btn-file :file').on('fileselect', function(event, label) {
        $(this).parents('.input-group').find(':text').val(label);
    });
});

// Enable summernote on all textareas with class 'wysiwyg'
$(document).ready(function() {
    $('textarea.wysiwyg').summernote();
});

// file loader for loading text files into text areas
function loadFileAsText(input, textarea) {
    var fileToLoad = input.files[0];

    var fileReader = new FileReader();
    fileReader.onload = function(fileLoadedEvent) {
        // special handling for wysiwyg editor
        if (textarea.classList.contains("wysiwyg")) {
            $('.wysiwyg').summernote('code', fileLoadedEvent.target.result);
        } else {
        textarea.value = fileLoadedEvent.target.result;
        }
    };
    fileReader.readAsText(fileToLoad, "UTF-8");
}

// fix summernote link mangling
$('.wysiwyg').summernote({
    onCreateLink: function (url) {
        if (url.match(/(https?|mailto):\/\/?.*/)) return url;
        var baseUrl = jsRoutes.org.ieee_passau.controllers.MainController.calendar().url;
        if (!url.startsWith(baseUrl)) url = baseUrl + url;
        return url;
    }
});

// activate cookie notice
window.addEventListener("load", function(){
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
            "href": jsRoutes.org.ieee_passau.controllers.MainController.content("contact").url
        }
    })
});

// activate notification dismissal
$('#flash-notification').on('close.bs.alert', function () {
    $.post(jsRoutes.org.ieee_passau.controllers.UserController.dismissNotification().url)
});
