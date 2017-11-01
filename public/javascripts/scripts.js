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

// file loader for loading text files into textareas
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
        return url;
    }
});